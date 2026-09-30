package com.flowdesk.ticket.exception;

public class InvalidTicketTransitionException extends RuntimeException {

    public InvalidTicketTransitionException(String message) {
        super(message);
    }
}
