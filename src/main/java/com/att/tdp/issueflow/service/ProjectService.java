package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.project.CreateProjectRequest;
import com.att.tdp.issueflow.dto.project.ProjectResponse;
import com.att.tdp.issueflow.dto.project.UpdateProjectRequest;
import com.att.tdp.issueflow.dto.project.WorkloadEntry;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TicketRepository  ticketRepository;
    private final UserRepository    userRepository;
    private final UserService       userService;
    private final AuditLogService   auditLogService;

    // ---------------------------------------------------------------
    // GET all active projects
    // ---------------------------------------------------------------
    public List<ProjectResponse> getAllActiveProjects() {
        return projectRepository.findByDeletedAtIsNull()
                .stream()
                .map(ProjectResponse::from)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // GET all soft-deleted projects  (ADMIN only - enforced in controller)
    // ---------------------------------------------------------------
    public List<ProjectResponse> getDeletedProjects() {
        return projectRepository.findByDeletedAtIsNotNull()
                .stream()
                .map(ProjectResponse::from)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // GET project by ID  (active only)
    // ---------------------------------------------------------------
    public ProjectResponse getProjectById(Long id) {
        Project project = findActiveProjectOrThrow(id);
        return ProjectResponse.from(project);
    }

    // ---------------------------------------------------------------
    // CREATE project
    // ---------------------------------------------------------------
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, User currentUser) {
        User owner = userService.findUserOrThrow(request.getOwnerId());

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(owner)
                .build();

        Project saved = projectRepository.save(project);
        auditLogService.log("CREATE", "PROJECT", saved.getId(), currentUser);

        return ProjectResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // UPDATE project
    // ---------------------------------------------------------------
    @Transactional
    public ProjectResponse updateProject(Long id, UpdateProjectRequest request, User currentUser) {
        Project project = findActiveProjectOrThrow(id);

        if (request.getName() != null) {
            project.setName(request.getName());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }

        Project saved = projectRepository.save(project);
        auditLogService.log("UPDATE", "PROJECT", saved.getId(), currentUser);

        return ProjectResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // SOFT DELETE project
    // ---------------------------------------------------------------
    @Transactional
    public void softDeleteProject(Long id, User currentUser) {
        Project project = findActiveProjectOrThrow(id);
        LocalDateTime now = LocalDateTime.now();

        project.setDeletedAt(now);
        projectRepository.save(project);

        // Cascade soft-delete to all active tickets in this project
        List<Ticket> activeTickets = ticketRepository.findByProjectIdAndDeletedAtIsNull(id);
        activeTickets.forEach(t -> t.setDeletedAt(now));
        ticketRepository.saveAll(activeTickets);

        auditLogService.log("DELETE", "PROJECT", id, currentUser);
    }

    // ---------------------------------------------------------------
    // RESTORE soft-deleted project  (ADMIN only - enforced in controller)
    // ---------------------------------------------------------------
    @Transactional
    public ProjectResponse restoreProject(Long id, User currentUser) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));

        if (project.getDeletedAt() == null) {
            throw new IllegalStateException("Project is not deleted: " + id);
        }

        LocalDateTime cascadeTimestamp = project.getDeletedAt();
        project.setDeletedAt(null);
        Project saved = projectRepository.save(project);

        // Restore only tickets that were deleted as part of this project's cascade
        // (i.e. their deletedAt matches the project's deletion timestamp exactly)
        List<Ticket> cascadeDeleted = ticketRepository.findByProjectIdAndDeletedAtIsNotNull(id)
                .stream()
                .filter(t -> cascadeTimestamp.equals(t.getDeletedAt()))
                .collect(Collectors.toList());
        cascadeDeleted.forEach(t -> t.setDeletedAt(null));
        ticketRepository.saveAll(cascadeDeleted);

        auditLogService.log("RESTORE", "PROJECT", saved.getId(), currentUser);

        return ProjectResponse.from(saved);
    }

    // ---------------------------------------------------------------
    // WORKLOAD  (GET /projects/:id/workload)
    // ---------------------------------------------------------------
    public List<WorkloadEntry> getWorkload(Long projectId) {
        findActiveProjectOrThrow(projectId);   // 404 if project doesn't exist

        // Returns rows of [assigneeId (Long), count (Long)]
        List<Object[]> rows = ticketRepository.countOpenTicketsPerAssigneeInProject(projectId);

        // Fetch all relevant users in a single query instead of one per row
        List<Long> userIds = rows.stream().map(row -> (Long) row[0]).collect(Collectors.toList());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return rows.stream()
                .map(row -> {
                    Long userId = (Long) row[0];
                    long count  = (Long) row[1];
                    User user   = userMap.get(userId);
                    return WorkloadEntry.builder()
                            .userId(userId)
                            .username(user != null ? user.getUsername() : "unknown")
                            .openTicketCount(count)
                            .build();
                })
                .sorted(Comparator.comparingLong(WorkloadEntry::getOpenTicketCount))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Internal helper
    // ---------------------------------------------------------------
    public Project findActiveProjectOrThrow(Long id) {
        return projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }
}
