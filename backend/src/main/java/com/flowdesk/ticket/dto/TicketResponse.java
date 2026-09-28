package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import java.time.Instant;

/**
 * Intentionally carries only IDs for its associations (not names) - see
 * {@code TicketMapper}'s Javadoc for why. Phase 6 introduces an enriched
 * projection for list views once EntityGraph/DTO-projection N+1
 * prevention is in scope.
 */
public record TicketResponse(
        Long id,
        Long organizationId,
        Long projectId,
        Long teamId,
        Long createdById,
        Long assignedToId,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        Instant dueDate,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
