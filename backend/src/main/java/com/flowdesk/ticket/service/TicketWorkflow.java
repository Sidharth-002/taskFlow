package com.flowdesk.ticket.service;

import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.exception.InvalidTicketTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TicketWorkflow {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TicketStatus.OPEN, EnumSet.of(TicketStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(TicketStatus.IN_PROGRESS, EnumSet.of(TicketStatus.WAITING, TicketStatus.RESOLVED));
        ALLOWED_TRANSITIONS.put(TicketStatus.WAITING, EnumSet.of(TicketStatus.IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(TicketStatus.RESOLVED, EnumSet.of(TicketStatus.CLOSED));
        ALLOWED_TRANSITIONS.put(TicketStatus.CLOSED, EnumSet.noneOf(TicketStatus.class));
    }

    private TicketWorkflow() {
    }

    public static boolean isValidTransition(TicketStatus from, TicketStatus to) {
        return from == to || ALLOWED_TRANSITIONS.get(from).contains(to);
    }

    public static void validateTransition(TicketStatus from, TicketStatus to) {
        if (!isValidTransition(from, to)) {
            throw new InvalidTicketTransitionException(
                    "Cannot transition ticket from %s to %s".formatted(from, to));
        }
    }
}
