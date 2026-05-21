package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled service that automatically escalates overdue tickets one priority level per cycle.
 * LOW → MEDIUM → HIGH → CRITICAL.
 * When a ticket reaches CRITICAL while overdue, its is_overdue flag is set to true.
 * Runs every 60 seconds.
 */
@Service
@RequiredArgsConstructor
public class EscalationService {

    private final TicketRepository ticketRepository;
    private final AuditLogService  auditLogService;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void escalateOverdueTickets() {

        // Step 1: Promote non-CRITICAL overdue tickets one priority level
        List<Ticket> toEscalate = ticketRepository.findOverdueTicketsForEscalation();
        for (Ticket ticket : toEscalate) {
            TicketPriority next = nextPriority(ticket.getPriority());
            ticket.setPriority(next);
            if (next == TicketPriority.CRITICAL) {
                ticket.setOverdue(true);
            }
            ticketRepository.save(ticket);
            auditLogService.log("AUTO_ESCALATE", "TICKET", ticket.getId(), null);
        }

        // Step 2: Set is_overdue flag for CRITICAL tickets not yet flagged
        List<Ticket> criticalPending = ticketRepository.findOverdueCriticalWithoutFlag();
        for (Ticket ticket : criticalPending) {
            ticket.setOverdue(true);
            ticketRepository.save(ticket);
            auditLogService.log("AUTO_ESCALATE", "TICKET", ticket.getId(), null);
        }
    }

    private TicketPriority nextPriority(TicketPriority current) {
        return switch (current) {
            case LOW    -> TicketPriority.MEDIUM;
            case MEDIUM -> TicketPriority.HIGH;
            case HIGH   -> TicketPriority.CRITICAL;
            default     -> current;   // CRITICAL is idempotent (handled by step 2)
        };
    }
}
