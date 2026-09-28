package com.flowdesk.ticket.exception;

/**
 * The requested status change isn't a legal transition from the ticket's
 * current status (e.g. {@code OPEN -> CLOSED} directly). See
 * {@code ticket.service.TicketWorkflow} for the allowed transition graph.
 */
public class InvalidTicketTransitionException extends RuntimeException {

    public InvalidTicketTransitionException(String message) {
        super(message);
    }
}
