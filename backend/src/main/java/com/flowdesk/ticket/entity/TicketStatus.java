package com.flowdesk.ticket.entity;

/**
 * Ticket lifecycle state. Transitions between these are restricted by
 * business rules enforced in the service layer (see the ticket workflow
 * implementation added in Phase 4), not left open to arbitrary changes.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    WAITING,
    RESOLVED,
    CLOSED
}
