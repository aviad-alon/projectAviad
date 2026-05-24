package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.ticket.CreateTicketRequest;
import com.att.tdp.issueflow.dto.ticket.TicketResponse;
import com.att.tdp.issueflow.dto.ticket.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository           ticketRepository;
    @Mock private TicketDependencyRepository ticketDependencyRepository;
    @Mock private UserRepository             userRepository;
    @Mock private ProjectService             projectService;
    @Mock private AuditLogService            auditLogService;

    @InjectMocks
    private TicketService ticketService;

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------
    private User developer(long id, String username) {
        return User.builder().id(id).username(username)
                .email(username + "@example.com").fullName(username)
                .role(UserRole.DEVELOPER).password("pw").build();
    }

    private Project project(long id) {
        return Project.builder().id(id).name("Test Project").build();
    }

    private Ticket activeTicket(long id, TicketStatus status, TicketPriority priority) {
        return Ticket.builder()
                .id(id).title("Ticket " + id)
                .status(status).priority(priority).type(TicketType.TECHNICAL)
                .deletedAt(null).build();
    }

    // ==================================================================
    // createTicket - auto-assignment
    // ==================================================================

    @Test
    void createTicket_autoAssignment_selectsDevWithFewestOpenTickets() {
        User dev1 = developer(10L, "dev1");
        User dev2 = developer(20L, "dev2");
        long projectId = 1L;
        Project proj = project(projectId);

        when(projectService.findActiveProjectOrThrow(projectId)).thenReturn(proj);
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(List.of(dev1, dev2));

        // dev1 → 3 open, dev2 → 1 open
        when(ticketRepository.countOpenTicketsPerAssigneeInProject(projectId))
                .thenReturn(List.of(new Object[]{10L, 3L}, new Object[]{20L, 1L}));

        Ticket saved = activeTicket(100L, TicketStatus.TODO, TicketPriority.MEDIUM);
        saved.setAssignee(dev2);
        saved.setProject(proj);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(saved);

        CreateTicketRequest req = CreateTicketRequest.builder()
                .title("New Feature").priority(TicketPriority.MEDIUM)
                .type(TicketType.FEATURE).projectId(projectId).build();

        ticketService.createTicket(req, developer(99L, "manager"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignee()).isEqualTo(dev2);
        // Auto-assignment must be recorded with SYSTEM actor
        verify(auditLogService).log("AUTO_ASSIGN", "TICKET", 100L, null);
    }

    @Test
    void createTicket_allDevsNewToProject_assignsFirstDev() {
        // Both devs have 0 tickets - first one in the list should be picked
        User dev1 = developer(1L, "dev1");
        User dev2 = developer(2L, "dev2");
        long projectId = 2L;
        Project proj = project(projectId);

        when(projectService.findActiveProjectOrThrow(projectId)).thenReturn(proj);
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(List.of(dev1, dev2));
        when(ticketRepository.countOpenTicketsPerAssigneeInProject(projectId))
                .thenReturn(Collections.emptyList()); // nobody has tickets yet

        Ticket saved = activeTicket(50L, TicketStatus.TODO, TicketPriority.LOW);
        saved.setAssignee(dev1);
        saved.setProject(proj);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(saved);

        CreateTicketRequest req = CreateTicketRequest.builder()
                .title("Task").priority(TicketPriority.LOW)
                .type(TicketType.TECHNICAL).projectId(projectId).build();

        ticketService.createTicket(req, developer(99L, "manager"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        // dev1 (id=1) wins - lowest ID breaks the 0-vs-0 tie (oldest registrant first)
        assertThat(captor.getValue().getAssignee()).isEqualTo(dev1);
        verify(auditLogService).log("AUTO_ASSIGN", "TICKET", 50L, null);
    }

    @Test
    void createTicket_tiedWorkload_assignsDevWithLowerId() {
        // dev2 appears first in findByRole() but has a higher ID → dev1 should win
        User dev1 = developer(1L, "dev1");
        User dev2 = developer(2L, "dev2");
        long projectId = 5L;
        Project proj = project(projectId);

        when(projectService.findActiveProjectOrThrow(projectId)).thenReturn(proj);
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(List.of(dev2, dev1));  // dev2 listed first

        // Both have exactly 1 open ticket - equal workload
        when(ticketRepository.countOpenTicketsPerAssigneeInProject(projectId))
                .thenReturn(List.of(new Object[]{1L, 1L}, new Object[]{2L, 1L}));

        Ticket saved = activeTicket(80L, TicketStatus.TODO, TicketPriority.LOW);
        saved.setAssignee(dev1);
        saved.setProject(proj);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(saved);

        ticketService.createTicket(
                CreateTicketRequest.builder().title("Task").priority(TicketPriority.LOW)
                        .type(TicketType.TECHNICAL).projectId(projectId).build(),
                developer(99L, "manager"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        // dev1 (id=1) wins despite being listed second - lower ID = older registrant
        assertThat(captor.getValue().getAssignee()).isEqualTo(dev1);
    }

    @Test
    void createTicket_noDevelopersInSystem_assigneeIsNull() {
        long projectId = 3L;
        Project proj = project(projectId);

        when(projectService.findActiveProjectOrThrow(projectId)).thenReturn(proj);
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(Collections.emptyList());

        Ticket saved = activeTicket(60L, TicketStatus.TODO, TicketPriority.LOW);
        saved.setProject(proj);
        saved.setAssignee(null);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(saved);

        User manager = User.builder().id(5L).username("mgr").email("mgr@x.com")
                .fullName("Mgr").role(UserRole.ADMIN).password("pw").build();
        CreateTicketRequest req = CreateTicketRequest.builder()
                .title("Unassigned Task").priority(TicketPriority.LOW)
                .type(TicketType.TECHNICAL).projectId(projectId).build();

        ticketService.createTicket(req, manager);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignee()).isNull();
    }

    @Test
    void createTicket_explicitAssigneeNotFound_throwsResourceNotFoundException() {
        long projectId = 6L;
        Project proj = project(projectId);

        when(projectService.findActiveProjectOrThrow(projectId)).thenReturn(proj);
        when(userRepository.findById(77L)).thenReturn(Optional.empty());

        CreateTicketRequest req = CreateTicketRequest.builder()
                .title("Task").priority(TicketPriority.LOW)
                .type(TicketType.TECHNICAL).projectId(projectId)
                .assigneeId(77L).build();

        assertThatThrownBy(() -> ticketService.createTicket(req, developer(99L, "manager")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("77");

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void createTicket_explicitAssigneeId_usesProvidedUser() {
        long projectId = 4L;
        Project proj = project(projectId);
        User specificDev = developer(30L, "specificDev");

        when(projectService.findActiveProjectOrThrow(projectId)).thenReturn(proj);
        when(userRepository.findById(30L)).thenReturn(Optional.of(specificDev));

        Ticket saved = activeTicket(70L, TicketStatus.TODO, TicketPriority.HIGH);
        saved.setAssignee(specificDev);
        saved.setProject(proj);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(saved);

        CreateTicketRequest req = CreateTicketRequest.builder()
                .title("Critical Bug").priority(TicketPriority.HIGH)
                .type(TicketType.BUG).projectId(projectId)
                .assigneeId(30L)   // explicit - skip auto-assignment
                .build();

        ticketService.createTicket(req, developer(99L, "manager"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignee()).isEqualTo(specificDev);

        // Auto-assignment logic (findByRole + countOpenTickets) must NOT be invoked
        verify(userRepository, never()).findByRole(any(UserRole.class));
        verify(ticketRepository, never()).countOpenTicketsPerAssigneeInProject(any());
    }

    // ==================================================================
    // softDeleteTicket
    // ==================================================================

    @Test
    void softDeleteTicket_setsDeletedAt_andLogsAudit() {
        Ticket ticket = activeTicket(5L, TicketStatus.TODO, TicketPriority.LOW);
        when(ticketRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        User actor = developer(1L, "alice");
        ticketService.softDeleteTicket(5L, actor);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
        assertThat(captor.getValue().getDeletedAt()).isBeforeOrEqualTo(LocalDateTime.now());

        verify(auditLogService).log(eq("DELETE"), eq("TICKET"), eq(5L), eq(actor));
    }

    @Test
    void softDeleteTicket_ticketNotFound_throwsResourceNotFoundException() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.softDeleteTicket(404L, developer(1L, "alice")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ticketRepository, never()).save(any());
    }

    // ==================================================================
    // restoreTicket
    // ==================================================================

    @Test
    void restoreTicket_notDeleted_throwsIllegalStateException() {
        Ticket active = activeTicket(15L, TicketStatus.TODO, TicketPriority.LOW);
        // deletedAt is null → ticket is not deleted
        when(ticketRepository.findById(15L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> ticketService.restoreTicket(15L, developer(1L, "alice")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("15");

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void restoreTicket_clearsDeletedAt_andLogsRestore() {
        Ticket deleted = activeTicket(8L, TicketStatus.TODO, TicketPriority.MEDIUM);
        deleted.setDeletedAt(LocalDateTime.now().minusDays(1));   // was deleted

        when(ticketRepository.findById(8L)).thenReturn(Optional.of(deleted));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(deleted);

        User actor = developer(2L, "bob");
        ticketService.restoreTicket(8L, actor);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNull();

        verify(auditLogService).log(eq("RESTORE"), eq("TICKET"), eq(8L), eq(actor));
    }

    // ==================================================================
    // updateTicket
    // ==================================================================

    @Test
    void updateTicket_updatesStatusAndPriority_andLogsAudit() {
        Ticket ticket = activeTicket(9L, TicketStatus.TODO, TicketPriority.LOW);
        when(ticketRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(ticket));

        Ticket saved = activeTicket(9L, TicketStatus.IN_PROGRESS, TicketPriority.HIGH);
        when(ticketRepository.save(any(Ticket.class))).thenReturn(saved);

        UpdateTicketRequest req = UpdateTicketRequest.builder()
                .status(TicketStatus.IN_PROGRESS)
                .priority(TicketPriority.HIGH)
                .build();

        User actor = developer(1L, "alice");
        TicketResponse response = ticketService.updateTicket(9L, req, actor);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(captor.getValue().getPriority()).isEqualTo(TicketPriority.HIGH);

        verify(auditLogService).log(eq("UPDATE"), eq("TICKET"), eq(9L), eq(actor));
    }

    @Test
    void updateTicket_nullFieldsIgnored_onlyProvidedFieldsUpdated() {
        Ticket ticket = activeTicket(11L, TicketStatus.TODO, TicketPriority.LOW);
        when(ticketRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        // Only title is set - status and priority stay unchanged
        UpdateTicketRequest req = UpdateTicketRequest.builder().title("Updated Title").build();

        ticketService.updateTicket(11L, req, developer(1L, "alice"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Updated Title");
        assertThat(captor.getValue().getStatus()).isEqualTo(TicketStatus.TODO);    // unchanged
        assertThat(captor.getValue().getPriority()).isEqualTo(TicketPriority.LOW); // unchanged
    }

    @Test
    void updateTicket_doneTicket_throwsIllegalStateException() {
        Ticket doneTicket = activeTicket(20L, TicketStatus.DONE, TicketPriority.MEDIUM);
        when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(doneTicket));

        UpdateTicketRequest req = UpdateTicketRequest.builder().title("New Title").build();

        assertThatThrownBy(() -> ticketService.updateTicket(20L, req, developer(1L, "alice")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DONE");

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateTicket_backwardStatusTransition_throwsIllegalArgumentException() {
        Ticket ticket = activeTicket(21L, TicketStatus.IN_REVIEW, TicketPriority.LOW);
        when(ticketRepository.findByIdAndDeletedAtIsNull(21L)).thenReturn(Optional.of(ticket));

        UpdateTicketRequest req = UpdateTicketRequest.builder()
                .status(TicketStatus.TODO)   // backward: IN_REVIEW -> TODO is not allowed
                .build();

        assertThatThrownBy(() -> ticketService.updateTicket(21L, req, developer(1L, "alice")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backward");

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateTicket_manualPriorityChange_clearsIsOverdueFlag() {
        Ticket ticket = activeTicket(30L, TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL);
        ticket.setOverdue(true);   // was auto-escalated
        when(ticketRepository.findByIdAndDeletedAtIsNull(30L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        UpdateTicketRequest req = UpdateTicketRequest.builder()
                .priority(TicketPriority.HIGH)   // manual downgrade
                .build();

        ticketService.updateTicket(30L, req, developer(1L, "alice"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().isOverdue()).isFalse();
    }

    @Test
    void updateTicket_transitionToDoneWithOpenBlocker_throwsIllegalStateException() {
        Ticket ticket = activeTicket(22L, TicketStatus.IN_REVIEW, TicketPriority.MEDIUM);
        when(ticketRepository.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(ticket));

        // A blocker ticket that is still IN_PROGRESS (not DONE)
        Ticket openBlocker = activeTicket(99L, TicketStatus.IN_PROGRESS, TicketPriority.LOW);
        TicketDependency dep = TicketDependency.builder().ticket(ticket).blockedBy(openBlocker).build();
        when(ticketDependencyRepository.findByTicketId(22L)).thenReturn(List.of(dep));

        UpdateTicketRequest req = UpdateTicketRequest.builder()
                .status(TicketStatus.DONE)
                .build();

        assertThatThrownBy(() -> ticketService.updateTicket(22L, req, developer(1L, "alice")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unresolved blocker");

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void updateTicket_nonDeveloperAssignee_throwsIllegalArgumentException() {
        Ticket ticket = activeTicket(9L, TicketStatus.TODO, TicketPriority.LOW);
        when(ticketRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(ticket));

        User manager = User.builder().id(5L).username("mgr").email("mgr@x.com")
                .fullName("Mgr").role(UserRole.ADMIN).password("pw").build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(manager));

        UpdateTicketRequest req = UpdateTicketRequest.builder()
                .assigneeId(5L)
                .build();

        assertThatThrownBy(() -> ticketService.updateTicket(9L, req, developer(1L, "alice")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEVELOPER");

        verify(ticketRepository, never()).save(any());
    }
}
