package com.att.tdp.issueflow.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a blocker relationship between two tickets.
 * ticket is blocked by blockedBy.
 *
 * Maps to the ticket_dependencies table (composite PK).
 */
@Entity
@Table(name = "ticket_dependencies")
@IdClass(TicketDependencyId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"ticket", "blockedBy"})
public class TicketDependency {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @EqualsAndHashCode.Include
    private Ticket ticket;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @EqualsAndHashCode.Include
    private Ticket blockedBy;
}
