package com.att.tdp.issueflow.scheduler;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Background job that runs every 60 seconds and escalates any overdue ticket
 * (dueDate < NOW, status != DONE, priority != HIGH) to HIGH priority.
 *
 * Requires @EnableScheduling on IssueFlowApplication - already added.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EscalationScheduler {

    private final TicketRepository ticketRepository;
    private final AuditLogService  auditLogService;

    @Scheduled(fixedDelay = 60_000)   // runs 60 s after the previous execution finishes
    @Transactional
    public void escalateOverdueTickets() {
        List<Ticket> overdue = ticketRepository.findOverdueTicketsForEscalation();

        if (overdue.isEmpty()) {
            return;
        }

        for (Ticket ticket : overdue) {
            ticket.setPriority(TicketPriority.HIGH);
            ticketRepository.save(ticket);
            // performedBy = null → actor recorded as "SYSTEM" in AuditLog
            auditLogService.log("AUTO_ESCALATE", "TICKET", ticket.getId(), null);
        }

        log.info("[EscalationScheduler] Escalated {} overdue ticket(s) to HIGH priority",
                overdue.size());
    }
}
