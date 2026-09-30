package com.flowdesk.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.exception.InvalidTicketTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TicketWorkflowTest {

    private static final Map<TicketStatus, Set<TicketStatus>> EXPECTED_ALLOWED = new EnumMap<>(TicketStatus.class);

    static {
        EXPECTED_ALLOWED.put(TicketStatus.OPEN, EnumSet.of(TicketStatus.IN_PROGRESS));
        EXPECTED_ALLOWED.put(TicketStatus.IN_PROGRESS, EnumSet.of(TicketStatus.WAITING, TicketStatus.RESOLVED));
        EXPECTED_ALLOWED.put(TicketStatus.WAITING, EnumSet.of(TicketStatus.IN_PROGRESS));
        EXPECTED_ALLOWED.put(TicketStatus.RESOLVED, EnumSet.of(TicketStatus.CLOSED));
        EXPECTED_ALLOWED.put(TicketStatus.CLOSED, EnumSet.noneOf(TicketStatus.class));
    }

    static java.util.stream.Stream<TicketStatus> allStatuses() {
        return java.util.Arrays.stream(TicketStatus.values());
    }

    @ParameterizedTest
    @MethodSource("allStatuses")
    void resubmittingTheSameStatus_isAlwaysAllowed(TicketStatus status) {
        assertThat(TicketWorkflow.isValidTransition(status, status)).isTrue();
    }

    @Test
    void exhaustivelyMatchesTheDocumentedTransitionGraph() {
        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                boolean expected = from == to || EXPECTED_ALLOWED.get(from).contains(to);
                assertThat(TicketWorkflow.isValidTransition(from, to))
                        .as("%s -> %s".formatted(from, to))
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void openToClosedDirectly_isRejected() {
        assertThatThrownBy(() -> TicketWorkflow.validateTransition(TicketStatus.OPEN, TicketStatus.CLOSED))
                .isInstanceOf(InvalidTicketTransitionException.class)
                .hasMessageContaining("OPEN")
                .hasMessageContaining("CLOSED");
    }

    @Test
    void closedIsTerminal_noTransitionsOut() {
        for (TicketStatus to : TicketStatus.values()) {
            if (to == TicketStatus.CLOSED) {
                continue;
            }
            assertThat(TicketWorkflow.isValidTransition(TicketStatus.CLOSED, to))
                    .as("CLOSED -> %s".formatted(to))
                    .isFalse();
        }
    }

    @Test
    void validTransition_doesNotThrow() {
        TicketWorkflow.validateTransition(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
        TicketWorkflow.validateTransition(TicketStatus.IN_PROGRESS, TicketStatus.WAITING);
        TicketWorkflow.validateTransition(TicketStatus.WAITING, TicketStatus.IN_PROGRESS);
        TicketWorkflow.validateTransition(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);
        TicketWorkflow.validateTransition(TicketStatus.RESOLVED, TicketStatus.CLOSED);
    }
}
