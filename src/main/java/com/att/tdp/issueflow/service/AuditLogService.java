package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.audit.AuditLogResponse;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records a state-changing action in the audit log.
     *
     * @param action      The operation performed: CREATE, UPDATE, DELETE, RESTORE
     * @param entityType  The domain object type: TICKET, PROJECT, COMMENT, etc.
     * @param entityId    The ID of the affected object
     * @param performedBy The user who triggered the action (null for SYSTEM actions)
     */
    public void log(String action, String entityType, Long entityId, User performedBy) {
        String actor = (performedBy == null) ? "SYSTEM" : "USER";

        AuditLog entry = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .performedBy(performedBy)
                .actor(actor)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(entry);
    }

    /**
     * Filtered query - any combination of entityType, entityId, and action.
     * Falls back to findAll() when no filters are supplied.
     */
    public List<AuditLogResponse> getAuditLogs(String entityType, Long entityId, String action) {
        List<AuditLog> logs;

        if (entityType != null && entityId != null) {
            logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
        } else if (entityType != null) {
            logs = auditLogRepository.findByEntityTypeOrderByTimestampDesc(entityType);
        } else if (action != null) {
            logs = auditLogRepository.findByActionOrderByTimestampDesc(action);
        } else {
            logs = auditLogRepository.findAll();
        }

        return logs.stream()
                .map(AuditLogResponse::from)
                .collect(Collectors.toList());
    }
}
