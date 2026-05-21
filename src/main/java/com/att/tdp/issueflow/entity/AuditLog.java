package com.att.tdp.issueflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Append-only audit trail of all state-changing actions in the system.
 * Records are never updated or deleted - only inserted.
 */
@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "performedBy")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** The operation performed: CREATE, UPDATE, DELETE, RESTORE, ADD_COMMENT, AUTO_ESCALATE, UPLOAD_ATTACHMENT */
    @Column(nullable = false, length = 30)
    private String action;

    /** The domain object type: TICKET, PROJECT, COMMENT, etc. */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /** The ID of the affected domain object */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** The user who performed the action (nullable - preserved even if user is deleted) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User performedBy;

    /** Who triggered the action: USER or SYSTEM (for scheduler) */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String actor = "USER";

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
