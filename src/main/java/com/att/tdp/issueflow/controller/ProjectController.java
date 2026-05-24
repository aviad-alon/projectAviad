package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.project.CreateProjectRequest;
import com.att.tdp.issueflow.dto.project.ProjectResponse;
import com.att.tdp.issueflow.dto.project.UpdateProjectRequest;
import com.att.tdp.issueflow.dto.project.WorkloadEntry;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.service.ProjectService;
import com.att.tdp.issueflow.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final UserService    userService;

    // POST /api/projects
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(request, currentUser));
    }

    // GET /api/projects
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllActiveProjects());
    }

    // GET /api/projects/deleted  - ADMIN only, MUST be declared before /{id}
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/deleted")
    public ResponseEntity<List<ProjectResponse>> getDeletedProjects() {
        return ResponseEntity.ok(projectService.getDeletedProjects());
    }

    // GET /api/projects/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    // PATCH /api/projects/{id}
    @PatchMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.ok(projectService.updateProject(id, request, currentUser));
    }

    // DELETE /api/projects/{id}  - soft delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        projectService.softDeleteProject(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    // POST /api/projects/{id}/restore  - ADMIN only
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/restore")
    public ResponseEntity<ProjectResponse> restoreProject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.ok(projectService.restoreProject(id, currentUser));
    }

    // GET /api/projects/{id}/workload
    @GetMapping("/{id}/workload")
    public ResponseEntity<List<WorkloadEntry>> getWorkload(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getWorkload(id));
    }
}
