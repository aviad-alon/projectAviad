package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.ticket.*;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.service.CsvService;
import com.att.tdp.issueflow.service.DependencyService;
import com.att.tdp.issueflow.service.TicketService;
import com.att.tdp.issueflow.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService      ticketService;
    private final DependencyService  dependencyService;
    private final CsvService         csvService;
    private final UserService        userService;

    // POST /api/tickets
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.createTicket(request, currentUser));
    }

    // GET /api/tickets/deleted  - MUST be declared before /{id}
    @GetMapping("/deleted")
    public ResponseEntity<List<TicketResponse>> getDeletedTickets(@RequestParam Long projectId) {
        return ResponseEntity.ok(ticketService.getDeletedTicketsByProject(projectId));
    }

    // GET /api/tickets/export  - MUST be declared before /{id}
    @GetMapping("/export")
    public void exportCsv(@RequestParam Long projectId,
                          HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"tickets.csv\"");
        csvService.exportTicketsToCsv(projectId, response.getWriter());
    }

    // GET /api/tickets?projectId=
    @GetMapping
    public ResponseEntity<List<TicketResponse>> getActiveTickets(@RequestParam Long projectId) {
        return ResponseEntity.ok(ticketService.getActiveTicketsByProject(projectId));
    }

    // GET /api/tickets/{id}
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.getTicketById(id));
    }

    // PATCH /api/tickets/{id}
    @PatchMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.ok(ticketService.updateTicket(id, request, currentUser));
    }

    // DELETE /api/tickets/{id}  - soft delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        ticketService.softDeleteTicket(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    // POST /api/tickets/{id}/restore
    @PostMapping("/{id}/restore")
    public ResponseEntity<TicketResponse> restoreTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.ok(ticketService.restoreTicket(id, currentUser));
    }

    // GET /api/tickets/{id}/dependencies
    @GetMapping("/{id}/dependencies")
    public ResponseEntity<List<TicketResponse>> getDependencies(@PathVariable Long id) {
        return ResponseEntity.ok(dependencyService.getDependencies(id));
    }

    // POST /api/tickets/{id}/dependencies
    @PostMapping("/{id}/dependencies")
    public ResponseEntity<Void> addDependency(
            @PathVariable Long id,
            @Valid @RequestBody AddDependencyRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        dependencyService.addDependency(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // DELETE /api/tickets/{id}/dependencies/{blockedById}
    @DeleteMapping("/{id}/dependencies/{blockedById}")
    public ResponseEntity<Void> removeDependency(
            @PathVariable Long id,
            @PathVariable Long blockedById,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        dependencyService.removeDependency(id, blockedById, currentUser);
        return ResponseEntity.noContent().build();
    }

    // POST /api/tickets/import
    @PostMapping("/import")
    public ResponseEntity<CsvImportResult> importCsv(
            @RequestParam Long projectId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal) throws IOException {
        User currentUser = userService.findByUsernameOrThrow(principal.getUsername());
        return ResponseEntity.ok(
                csvService.importTicketsFromCsv(file.getInputStream(), projectId, currentUser));
    }
}
