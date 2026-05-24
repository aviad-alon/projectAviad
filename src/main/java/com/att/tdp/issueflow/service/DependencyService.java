package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.ticket.AddDependencyRequest;
import com.att.tdp.issueflow.dto.ticket.TicketResponse;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.entity.TicketDependencyId;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DependencyService {

    private final TicketDependencyRepository dependencyRepository;
    private final TicketService              ticketService;
    private final AuditLogService            auditLogService;

    // ---------------------------------------------------------------
    // GET all blockers for a ticket
    // ---------------------------------------------------------------
    public List<TicketResponse> getDependencies(Long ticketId) {
        ticketService.findActiveTicketOrThrow(ticketId);   // 404 if ticket doesn't exist

        return dependencyRepository.findByTicketId(ticketId)
                .stream()
                .map(dep -> TicketResponse.from(dep.getBlockedBy()))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // ADD a blocker dependency
    // ---------------------------------------------------------------
    @Transactional
    public void addDependency(Long ticketId, AddDependencyRequest request, User currentUser) {
        Long blockedById = request.getBlockedBy();

        // Business rule: a ticket cannot block itself
        if (ticketId.equals(blockedById)) {
            throw new IllegalArgumentException("A ticket cannot block itself (id=" + ticketId + ")");
        }

        Ticket ticket    = ticketService.findActiveTicketOrThrow(ticketId);
        Ticket blockedBy = ticketService.findActiveTicketOrThrow(blockedById);

        // Both tickets must belong to the same project
        if (!ticket.getProject().getId().equals(blockedBy.getProject().getId())) {
            throw new IllegalArgumentException(
                    "Cannot create dependency between tickets from different projects");
        }

        // Prevent duplicate relationships
        if (dependencyRepository.existsByTicketIdAndBlockedById(ticketId, blockedById)) {
            throw new ConflictException(
                    "Dependency already exists: ticket " + ticketId +
                    " is already blocked by " + blockedById);
        }

        // Prevent circular dependencies using BFS over the existing dependency graph
        if (wouldCreateCycle(ticketId, blockedById)) {
            throw new IllegalArgumentException(
                    "Cannot add dependency: would create a circular dependency involving ticket " + ticketId);
        }

        TicketDependency dependency = TicketDependency.builder()
                .ticket(ticket)
                .blockedBy(blockedBy)
                .build();

        dependencyRepository.save(dependency);
        auditLogService.log("CREATE", "DEPENDENCY",
                ticketId, currentUser);
    }

    // ---------------------------------------------------------------
    // REMOVE a blocker dependency
    // ---------------------------------------------------------------
    @Transactional
    public void removeDependency(Long ticketId, Long blockedById, User currentUser) {
        ticketService.findActiveTicketOrThrow(ticketId);

        TicketDependencyId key = new TicketDependencyId(ticketId, blockedById);

        TicketDependency dependency = dependencyRepository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dependency not found: ticket " + ticketId +
                        " blocked by " + blockedById));

        dependencyRepository.delete(dependency);
        auditLogService.log("DELETE", "DEPENDENCY", ticketId, currentUser);
    }

    // ---------------------------------------------------------------
    // BFS cycle detection  (internal)
    // ---------------------------------------------------------------

    /**
     * Returns true if adding the edge  ticketId <- newBlockerId  would create a cycle.
     *
     * Strategy: perform a BFS starting from newBlockerId and follow the existing
     * "blocked-by" chain (i.e. what transitively blocks newBlockerId).
     * If we ever reach ticketId, the proposed edge would close a loop.
     *
     * Example: A<-B, B<-C already exist.  Adding C<-A would form A<-B<-C<-A.
     * BFS starting from A: A->B (blocks A)->C (blocks B) -> reaches C == ticketId? No.
     * Wait, we are checking addDependency(C, A), so ticketId=C, newBlockerId=A.
     * BFS from A: findBlockerIds(A)=[B], findBlockerIds(B)=[C] -> current==C -> true.
     */
    private boolean wouldCreateCycle(Long ticketId, Long newBlockerId) {
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(newBlockerId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(ticketId)) return true;
            if (!visited.add(current)) continue;   // already processed, skip
            dependencyRepository.findBlockerIdsByTicketId(current).forEach(queue::add);
        }
        return false;
    }
}
