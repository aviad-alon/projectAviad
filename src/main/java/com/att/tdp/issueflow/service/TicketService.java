package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.ticket.CreateTicketRequest;
import com.att.tdp.issueflow.dto.ticket.TicketResponse;
import com.att.tdp.issueflow.dto.ticket.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Service handling ticket lifecycle - creation, updates, soft-delete, restore, and auto-assignment. */
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository           ticketRepository;
    private final TicketDependencyRepository ticketDependencyRepository;
    private final UserRepository             userRepository;
    private final ProjectService             projectService;
    private final AuditLogService            auditLogService;

    // ---------------------------------------------------------------
    // GET active tickets by project
    // ---------------------------------------------------------------
    /** Returns all non-deleted tickets belonging to the given project. */
    public List<TicketResponse> getActiveTicketsByProject(Long projectId) {
        projectService.findActiveProjectOrThrow(projectId);
        return ticketRepository.findByProjectIdAndDeletedAtIsNull(projectId)
                .stream()
                .map(TicketResponse::from)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // GET soft-deleted tickets by project
    // ---------------------------------------------------------------
    /** Returns all soft-deleted tickets belonging to the given active project. */
    public List<TicketResponse> getDeletedTicketsByProject(Long projectId) {
        projectService.findActiveProjectOrThrow(projectId);
        return ticketRepository.findByProjectIdAndDeletedAtIsNotNull(projectId)
                .stream()
                .map(TicketResponse::from)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // GET ticket by ID
    // ---------------------------------------------------------------
    /** Returns a single active ticket by ID, throwing 404 if not found or deleted. */
    public TicketResponse getTicketById(Long id) {
        return TicketResponse.from(findActiveTicketOrThrow(id));
    }

    // ---------------------------------------------------------------
    // CREATE ticket  (with auto-assignment)
    // ---------------------------------------------------------------
    /**
     * Creates a new ticket inside the specified project.
     * If no assignee is given, auto-assigns to the developer with the fewest open tickets.
     */
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, User currentUser) {
        Project project = projectService.findActiveProjectOrThrow(request.getProjectId());

        User assignee = resolveAssignee(request.getAssigneeId(), request.getProjectId());

        Ticket ticket = Ticket.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : TicketStatus.TODO)
                .priority(request.getPriority())
                .type(request.getType())
                .project(project)
                .assignee(assignee)
                .dueDate(request.getDueDate())
                .build();

        Ticket saved = ticketRepository.save(ticket);
        auditLogService.log("CREATE", "TICKET", saved.getId(), currentUser);

        // Log system-level auto-assignment when no explicit assignee was supplied
        if (request.getAssigneeId() == null && assignee != null) {
            auditLogService.log("AUTO_ASSIGN", "TICKET", saved.getId(), null);
        }

        return TicketResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // UPDATE ticket  (partial update - only non-null fields)
    // ---------------------------------------------------------------
    /** Applies non-null fields from the request to the existing ticket and logs the update. */
    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request, User currentUser) {
        Ticket ticket = findActiveTicketOrThrow(id);

        // A DONE ticket is immutable - no further updates allowed
        if (ticket.getStatus() == TicketStatus.DONE) {
            throw new IllegalStateException(
                    "Cannot update a ticket that is already DONE (id=" + id + ")");
        }

        // Status transitions are forward-only: TODO -> IN_PROGRESS -> IN_REVIEW -> DONE
        if (request.getStatus() != null &&
                request.getStatus().ordinal() < ticket.getStatus().ordinal()) {
            throw new IllegalArgumentException(
                    "Invalid status transition: cannot move backward from " +
                    ticket.getStatus() + " to " + request.getStatus());
        }

        // Cannot transition to DONE if there are unresolved (non-DONE) blocker tickets
        if (request.getStatus() == TicketStatus.DONE) {
            boolean hasOpenBlockers = ticketDependencyRepository.findByTicketId(id)
                    .stream()
                    .anyMatch(dep -> dep.getBlockedBy().getStatus() != TicketStatus.DONE);
            if (hasOpenBlockers) {
                throw new IllegalStateException(
                        "Cannot mark ticket as DONE - it has unresolved blocker tickets (id=" + id + ")");
            }
        }

        if (request.getTitle() != null)       ticket.setTitle(request.getTitle());
        if (request.getDescription() != null) ticket.setDescription(request.getDescription());
        if (request.getStatus() != null)      ticket.setStatus(request.getStatus());
        if (request.getPriority() != null) {
            ticket.setPriority(request.getPriority());
            ticket.setOverdue(false);   // manual priority change resets auto-escalation state
        }
        if (request.getDueDate() != null)     ticket.setDueDate(request.getDueDate());

        if (request.getAssigneeId() != null) {
            User newAssignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Assignee not found: " + request.getAssigneeId()));
            ticket.setAssignee(newAssignee);
        }

        Ticket saved = ticketRepository.save(ticket);
        auditLogService.log("UPDATE", "TICKET", saved.getId(), currentUser);

        return TicketResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // SOFT DELETE ticket
    // ---------------------------------------------------------------
    /** Marks the ticket as deleted by setting deletedAt to now, without removing the row. */
    @Transactional
    public void softDeleteTicket(Long id, User currentUser) {
        Ticket ticket = findActiveTicketOrThrow(id);
        ticket.setDeletedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        auditLogService.log("DELETE", "TICKET", id, currentUser);
    }

    // ---------------------------------------------------------------
    // RESTORE soft-deleted ticket  (ADMIN only - enforced in controller)
    // ---------------------------------------------------------------
    /** Clears deletedAt on a soft-deleted ticket, making it active again. */
    @Transactional
    public TicketResponse restoreTicket(Long id, User currentUser) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));

        if (ticket.getDeletedAt() == null) {
            throw new IllegalStateException("Ticket is not deleted: " + id);
        }

        ticket.setDeletedAt(null);
        Ticket saved = ticketRepository.save(ticket);
        auditLogService.log("RESTORE", "TICKET", saved.getId(), currentUser);

        return TicketResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // AUTO-ASSIGNMENT  (internal)
    // ---------------------------------------------------------------

    /**
     * If assigneeId is provided, validate and return that user.
     * Otherwise, find the DEVELOPER with the fewest open tickets in the project.
     */
    private User resolveAssignee(Long assigneeId, Long projectId) {
        if (assigneeId != null) {
            return userRepository.findById(assigneeId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Assignee not found: " + assigneeId));
        }

        // Collect all DEVELOPER users in the system
        List<User> developers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.DEVELOPER)
                .collect(Collectors.toList());

        if (developers.isEmpty()) {
            return null;   // no developers available - leave unassigned
        }

        // Build a map: developerId → open-ticket count in this project
        Map<Long, Long> ticketCountMap = ticketRepository
                .countOpenTicketsPerAssigneeInProject(projectId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));

        // Assign to the developer with the fewest open tickets; ties broken by lowest ID (oldest registrant)
        return developers.stream()
                .min(Comparator.comparingLong((User dev) -> ticketCountMap.getOrDefault(dev.getId(), 0L))
                        .thenComparingLong(User::getId))
                .orElse(developers.get(0));
    }

    // ---------------------------------------------------------------
    // Internal helper
    // ---------------------------------------------------------------
    /** Looks up a non-deleted ticket by ID and throws 404 if not found. */
    public Ticket findActiveTicketOrThrow(Long id) {
        return ticketRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }
}
