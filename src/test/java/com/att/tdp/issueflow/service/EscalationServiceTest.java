package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private AuditLogService  auditLogService;

    @InjectMocks
    private EscalationService escalationService;

    // ------------------------------------------------------------------
    // Fixture helper
    // ------------------------------------------------------------------
    private Ticket overdueTicket(long id, TicketPriority priority) {
        return Ticket.builder()
                .id(id).title("Ticket " + id)
                .status(TicketStatus.IN_PROGRESS)
                .priority(priority)
                .type(TicketType.TECHNICAL)
                .isOverdue(false)
                .build();
    }

    // ==================================================================
    // Priority escalation (LOW → MEDIUM → HIGH → CRITICAL)
    // ==================================================================

    @Test
    void escalate_lowPriorityTicket_promotedToMedium() {
        Ticket t = overdueTicket(1L, TicketPriority.LOW);
        when(ticketRepository.findOverdueTicketsForEscalation()).thenReturn(List.of(t));
        when(ticketRepository.findOverdueCriticalWithoutFlag()).thenReturn(List.of());
        when(ticketRepository.save(any(Ticket.class))).thenReturn(t);

        escalationService.escalateOverdueTickets();

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(captor.getValue().isOverdue()).isFalse();
        verify(auditLogService).log("AUTO_ESCALATE", "TICKET", 1L, null);
    }

    @Test
    void escalate_mediumPriorityTicket_promotedToHigh() {
        Ticket t = overdueTicket(2L, TicketPriority.MEDIUM);
        when(ticketRepository.findOverdueTicketsForEscalation()).thenReturn(List.of(t));
        when(ticketRepository.findOverdueCriticalWithoutFlag()).thenReturn(List.of());
        when(ticketRepository.save(any(Ticket.class))).thenReturn(t);

        escalationService.escalateOverdueTickets();

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(captor.getValue().isOverdue()).isFalse();
        verify(auditLogService).log("AUTO_ESCALATE", "TICKET", 2L, null);
    }

    @Test
    void escalate_highPriorityTicket_promotedToCriticalAndFlagSet() {
        Ticket t = overdueTicket(3L, TicketPriority.HIGH);
        when(ticketRepository.findOverdueTicketsForEscalation()).thenReturn(List.of(t));
        when(ticketRepository.findOverdueCriticalWithoutFlag()).thenReturn(List.of());
        when(ticketRepository.save(any(Ticket.class))).thenReturn(t);

        escalationService.escalateOverdueTickets();

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(captor.getValue().isOverdue()).isTrue();
        verify(auditLogService).log("AUTO_ESCALATE", "TICKET", 3L, null);
    }

    // ==================================================================
    // CRITICAL without flag - step 2
    // ==================================================================

    @Test
    void escalate_criticalTicketWithoutFlag_setsOverdueFlagWithoutPriorityChange() {
        Ticket t = overdueTicket(4L, TicketPriority.CRITICAL);
        when(ticketRepository.findOverdueTicketsForEscalation()).thenReturn(List.of());
        when(ticketRepository.findOverdueCriticalWithoutFlag()).thenReturn(List.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(t);

        escalationService.escalateOverdueTickets();

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(TicketPriority.CRITICAL); // unchanged
        assertThat(captor.getValue().isOverdue()).isTrue();
        verify(auditLogService).log("AUTO_ESCALATE", "TICKET", 4L, null);
    }

    // ==================================================================
    // No-op when nothing is overdue
    // ==================================================================

    @Test
    void escalate_noOverdueTickets_noSavesNoLogs() {
        when(ticketRepository.findOverdueTicketsForEscalation()).thenReturn(List.of());
        when(ticketRepository.findOverdueCriticalWithoutFlag()).thenReturn(List.of());

        escalationService.escalateOverdueTickets();

        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }
}
