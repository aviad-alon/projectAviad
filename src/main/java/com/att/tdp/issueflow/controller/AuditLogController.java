package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.audit.AuditLogResponse;
import com.att.tdp.issueflow.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * GET /api/audit-logs
     * Optional query params:
     *   ?entityType=TICKET
     *   ?entityType=TICKET&entityId=5
     *   ?action=CREATE
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long   entityId,
            @RequestParam(required = false) String action) {
        return ResponseEntity.ok(auditLogService.getAuditLogs(entityType, entityId, action));
    }
}
