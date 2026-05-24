package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.project.CreateProjectRequest;
import com.att.tdp.issueflow.dto.project.ProjectResponse;
import com.att.tdp.issueflow.dto.project.UpdateProjectRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private TicketRepository  ticketRepository;
    @Mock private UserService       userService;
    @Mock private AuditLogService   auditLogService;

    @InjectMocks
    private ProjectService projectService;

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------
    private User owner(long id) {
        return User.builder().id(id).username("owner" + id)
                .email("owner" + id + "@example.com").fullName("Owner " + id)
                .role(UserRole.ADMIN).password("pw").build();
    }

    private Project activeProject(long id, String name, User owner) {
        return Project.builder().id(id).name(name).owner(owner).deletedAt(null).build();
    }

    private User actor() {
        return User.builder().id(99L).username("actor")
                .email("actor@example.com").fullName("Actor")
                .role(UserRole.ADMIN).password("pw").build();
    }

    // ==================================================================
    // createProject
    // ==================================================================

    @Test
    void createProject_success_savesProjectAndLogsAudit() {
        User owner = owner(1L);
        User actor = actor();
        when(userService.findUserOrThrow(1L)).thenReturn(owner);

        Project saved = activeProject(10L, "Alpha", owner);
        when(projectRepository.save(any(Project.class))).thenReturn(saved);

        CreateProjectRequest req = CreateProjectRequest.builder()
                .name("Alpha").ownerId(1L).build();

        ProjectResponse response = projectService.createProject(req, actor);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Alpha");

        verify(projectRepository).save(any(Project.class));
        verify(auditLogService).log("CREATE", "PROJECT", 10L, actor);
    }

    @Test
    void createProject_ownerNotFound_throwsResourceNotFoundException() {
        when(userService.findUserOrThrow(99L))
                .thenThrow(new ResourceNotFoundException("User not found: 99"));

        CreateProjectRequest req = CreateProjectRequest.builder()
                .name("Beta").ownerId(99L).build();

        assertThatThrownBy(() -> projectService.createProject(req, actor()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(projectRepository, never()).save(any());
    }

    // ==================================================================
    // getProjectById
    // ==================================================================

    @Test
    void getProjectById_existingProject_returnsResponse() {
        User owner = owner(1L);
        Project project = activeProject(5L, "Gamma", owner);
        when(projectRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.getProjectById(5L);

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getName()).isEqualTo("Gamma");
    }

    @Test
    void getProjectById_notFound_throwsResourceNotFoundException() {
        when(projectRepository.findByIdAndDeletedAtIsNull(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    // ==================================================================
    // updateProject
    // ==================================================================

    @Test
    void updateProject_partialUpdate_onlyNonNullFieldsChanged() {
        User owner = owner(1L);
        Project project = activeProject(7L, "Old Name", owner);
        when(projectRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        // Only description is provided - name must stay unchanged
        UpdateProjectRequest req = UpdateProjectRequest.builder()
                .description("New description").build();

        projectService.updateProject(7L, req, actor());

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Old Name");        // unchanged
        assertThat(captor.getValue().getDescription()).isEqualTo("New description");
    }

    // ==================================================================
    // softDeleteProject
    // ==================================================================

    @Test
    void softDeleteProject_setsDeletedAt_andLogsAudit() {
        User owner = owner(1L);
        Project project = activeProject(3L, "ToDelete", owner);
        when(projectRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        User actor = actor();
        projectService.softDeleteProject(3L, actor);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
        assertThat(captor.getValue().getDeletedAt()).isBeforeOrEqualTo(LocalDateTime.now());

        verify(auditLogService).log("DELETE", "PROJECT", 3L, actor);
    }

    @Test
    void softDeleteProject_notFound_throwsResourceNotFoundException() {
        when(projectRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.softDeleteProject(999L, actor()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void softDeleteProject_cascadesToActiveTickets() {
        User owner = owner(1L);
        Project project = activeProject(3L, "ToDelete", owner);
        when(projectRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        Ticket t1 = Ticket.builder().id(1L).title("T1")
                .status(TicketStatus.TODO).priority(TicketPriority.LOW)
                .type(TicketType.BUG).project(project).build();
        Ticket t2 = Ticket.builder().id(2L).title("T2")
                .status(TicketStatus.IN_PROGRESS).priority(TicketPriority.MEDIUM)
                .type(TicketType.FEATURE).project(project).build();
        when(ticketRepository.findByProjectIdAndDeletedAtIsNull(3L)).thenReturn(List.of(t1, t2));

        projectService.softDeleteProject(3L, actor());

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        List<Ticket> saved = (List<Ticket>) captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(t -> t.getDeletedAt() != null);
    }

    // ==================================================================
    // restoreProject
    // ==================================================================

    @Test
    void restoreProject_clearsDeletedAt_andLogsRestore() {
        User owner = owner(1L);
        Project deleted = activeProject(4L, "Deleted", owner);
        deleted.setDeletedAt(LocalDateTime.now().minusDays(1));

        when(projectRepository.findById(4L)).thenReturn(Optional.of(deleted));
        when(projectRepository.save(any(Project.class))).thenReturn(deleted);

        User actor = actor();
        ProjectResponse response = projectService.restoreProject(4L, actor);
        assertThat(response.getId()).isEqualTo(4L);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNull();

        verify(auditLogService).log("RESTORE", "PROJECT", 4L, actor);
    }

    @Test
    void restoreProject_notDeleted_throwsIllegalStateException() {
        User owner = owner(1L);
        Project active = activeProject(5L, "Active", owner); // deletedAt is null

        when(projectRepository.findById(5L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> projectService.restoreProject(5L, actor()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("5");

        verify(projectRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void restoreProject_cascadesToDeletedTickets() {
        User owner = owner(1L);
        Project deleted = activeProject(4L, "Deleted", owner);
        deleted.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(projectRepository.findById(4L)).thenReturn(Optional.of(deleted));
        when(projectRepository.save(any(Project.class))).thenReturn(deleted);

        Ticket t1 = Ticket.builder().id(1L).title("T1")
                .status(TicketStatus.TODO).priority(TicketPriority.LOW)
                .type(TicketType.BUG).project(deleted)
                .deletedAt(LocalDateTime.now().minusDays(1)).build();
        when(ticketRepository.findByProjectIdAndDeletedAtIsNotNull(4L)).thenReturn(List.of(t1));

        projectService.restoreProject(4L, actor());

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        List<Ticket> saved = (List<Ticket>) captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getDeletedAt()).isNull();
    }

    @Test
    void restoreProject_notFound_throwsResourceNotFoundException() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.restoreProject(404L, actor()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }
}
