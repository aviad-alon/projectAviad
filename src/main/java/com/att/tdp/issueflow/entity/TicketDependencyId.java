package com.att.tdp.issueflow.entity;

import lombok.*;

import java.io.Serializable;

/**
 * Composite primary key for TicketDependency.
 * Represents the (ticket_id, blocked_by_id) pair.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketDependencyId implements Serializable {
    private Long ticket;
    private Long blockedBy;
}
