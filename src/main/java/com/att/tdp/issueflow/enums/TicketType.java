package com.att.tdp.issueflow.enums;

/**
 * Classifies the nature of a ticket.
 * - BUG: something is broken and needs fixing.
 * - FEATURE: new functionality to be added.
 * - TECHNICAL: infrastructure, refactoring, or tech-debt work.
 */
public enum TicketType {
    BUG,
    FEATURE,
    TECHNICAL
}
