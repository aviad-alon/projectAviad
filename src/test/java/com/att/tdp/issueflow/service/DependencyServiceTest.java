package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.ticket.AddDependencyRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.entity.TicketDependencyId;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependencyServiceTest {

    @Mock private TicketDependencyRepository dependencyRepository;
    @Mock private TicketService              ticketService;
    @Mock private AuditLogService            auditLogService;

    @InjectMocks
    private DependencyService dependencyService;

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------
    private User actor() {
        return User.builder().id(1L).username("actor")
                .email("actor@example.com").fullName("Actor")
                .role(UserRole.DEVELOPER).password("pw").build();
    }

    private Project project(long id) {
        return Project.builder().id(id).name("Project " + id).build();
    }

    private Ticket ticket(long id, Project project) {
        return Ticket.builder()
                .id(id).title("Ticket " + id)
                .status(TicketStatus.TODO).priority(TicketPriority.LOW)
                .type(TicketType.TECHNICAL).project(project).build();
    }

    // ==================================================================
    // addDependency
    // ==================================================================

    @Test
    void addDependency_success_savesAndLogsAudit() {
        Project proj = project(1L);
        Ticket t1 = ticket(10L, proj);
        Ticket t2 = ticket(20L, proj);

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(t1);
        when(ticketService.findActiveTicketOrThrow(20L)).thenReturn(t2);
        when(dependencyRepository.existsByTicketIdAndBlockedById(10L, 20L)).thenReturn(false);

        User actor = actor();
        dependencyService.addDependency(10L,
                AddDependencyRequest.builder().blockedBy(20L).build(), actor);

        verify(dependencyRepository).save(any(TicketDependency.class));
        verify(auditLogService).log("CREATE", "DEPENDENCY", 10L, actor);
    }

    @Test
    void addDependency_selfDependency_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> dependencyService.addDependency(5L,
                AddDependencyRequest.builder().blockedBy(5L).build(), actor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5");

        verify(dependencyRepository, never()).save(any());
    }

    @Test
    void addDependency_differentProjects_throwsIllegalArgumentException() {
        Ticket t1 = ticket(10L, project(1L));
        Ticket t2 = ticket(20L, project(2L));  // different project

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(t1);
        when(ticketService.findActiveTicketOrThrow(20L)).thenReturn(t2);

        assertThatThrownBy(() -> dependencyService.addDependency(10L,
                AddDependencyRequest.builder().blockedBy(20L).build(), actor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different projects");

        verify(dependencyRepository, never()).save(any());
    }

    @Test
    void addDependency_duplicate_throwsConflictException() {
        Project proj = project(1L);
        Ticket t1 = ticket(10L, proj);
        Ticket t2 = ticket(20L, proj);

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(t1);
        when(ticketService.findActiveTicketOrThrow(20L)).thenReturn(t2);
        when(dependencyRepository.existsByTicketIdAndBlockedById(10L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> dependencyService.addDependency(10L,
                AddDependencyRequest.builder().blockedBy(20L).build(), actor()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already blocked");

        verify(dependencyRepository, never()).save(any());
    }

    // ==================================================================
    // removeDependency
    // ==================================================================

    @Test
    void removeDependency_success_deletesAndLogsAudit() {
        Project proj = project(1L);
        Ticket t1 = ticket(10L, proj);
        Ticket t2 = ticket(20L, proj);
        TicketDependency dep = TicketDependency.builder().ticket(t1).blockedBy(t2).build();

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(t1);
        when(dependencyRepository.findById(new TicketDependencyId(10L, 20L)))
                .thenReturn(Optional.of(dep));

        User actor = actor();
        dependencyService.removeDependency(10L, 20L, actor);

        verify(dependencyRepository).delete(dep);
        verify(auditLogService).log("DELETE", "DEPENDENCY", 10L, actor);
    }

    @Test
    void removeDependency_dependencyNotFound_throwsResourceNotFoundException() {
        Project proj = project(1L);
        Ticket t1 = ticket(10L, proj);

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(t1);
        when(dependencyRepository.findById(new TicketDependencyId(10L, 99L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dependencyService.removeDependency(10L, 99L, actor()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(dependencyRepository, never()).delete(any());
    }

    // ==================================================================
    // getDependencies
    // ==================================================================

    @Test
    void getDependencies_returnsBlockerTickets() {
        Project proj = project(1L);
        Ticket main    = ticket(10L, proj);
        Ticket blocker = ticket(20L, proj);
        TicketDependency dep = TicketDependency.builder().ticket(main).blockedBy(blocker).build();

        when(ticketService.findActiveTicketOrThrow(10L)).thenReturn(main);
        when(dependencyRepository.findByTicketId(10L)).thenReturn(List.of(dep));

        var result = dependencyService.getDependencies(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(20L);
    }
}
