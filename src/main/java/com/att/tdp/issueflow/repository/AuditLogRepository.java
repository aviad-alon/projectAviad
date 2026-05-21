package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** All log entries for a specific entity, newest first */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(
            String entityType, Long entityId);

    /** All log entries by a specific user, newest first */
    List<AuditLog> findByPerformedByIdOrderByTimestampDesc(Long performedById);

    /** All log entries for a specific action type (e.g. "CREATE"), newest first */
    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    /** All log entries for a specific entity type (e.g. "TICKET"), newest first */
    List<AuditLog> findByEntityTypeOrderByTimestampDesc(String entityType);

    /** All log entries for a specific actor type (e.g. "SYSTEM"), newest first */
    List<AuditLog> findByActorOrderByTimestampDesc(String actor);
}
