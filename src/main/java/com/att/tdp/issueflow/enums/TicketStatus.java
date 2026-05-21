package com.att.tdp.issueflow.enums;

/**
 * Lifecycle states of a ticket.
 * Transitions are forward-only: TODO -> IN_PROGRESS -> IN_REVIEW -> DONE.
 * Ordinal values enforce ordering - higher ordinal = further along the lifecycle.
 */
public enum TicketStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE
}
