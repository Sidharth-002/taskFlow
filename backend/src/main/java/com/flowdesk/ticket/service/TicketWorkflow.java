package com.flowdesk.ticket.service;

import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.exception.InvalidTicketTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The ticket status state machine (spec Section 14):
 *
 * <pre>
 * OPEN -> IN_PROGRESS
 * IN_PROGRESS -> WAITING | RESOLVED
 * WAITING -> IN_PROGRESS
 * RESOLVED -> CLOSED
 * CLOSED -> (terminal)
 * </pre>
 *
 * <p>Deliberately a plain stateless utility, not a Spring bean: this is
 * pure domain logic with no dependencies, and every call site (currently
 * only {@code TicketService}) already runs inside a transactional service
 * method - there's nothing a DI container would add here.
 *
 * <p>Only the transitions the spec explicitly draws are allowed - e.g.
 * there's no {@code RESOLVED -> IN_PROGRESS} "reopen" path, even though a
 * real product would likely want one eventually. Adding it is a one-line
 * change to {@link #ALLOWED_TRANSITIONS} when a real requirement calls
 * for it, not a "for completeness" addition now.
 */
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

    /** Resubmitting the current status is treated as a no-op, not a transition. */
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
