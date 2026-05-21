package com.att.tdp.issueflow.enums;

/**
 * Priority levels for a ticket.
 * Overdue tickets (non-DONE, non-HIGH, non-CRITICAL) are automatically escalated to HIGH
 * by the EscalationScheduler. CRITICAL is not subject to automatic escalation.
 */
public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
