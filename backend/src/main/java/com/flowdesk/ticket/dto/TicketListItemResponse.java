package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import java.time.Instant;

/**
 * The Phase 6 enriched list projection: unlike {@link TicketResponse}
 * (IDs only, used for a single-ticket read), this carries the names of a
 * ticket's associations too, because a list view renders them directly
 * instead of making the client resolve every ID separately. Safe to do
 * per-row here specifically because {@code TicketRepository.findAll}
 * fetch-joins those associations up front (see its Javadoc) - without
 * that, mapping names here would be exactly the N+1 pattern
 * {@code TicketMapper} was originally written to avoid.
 */
public record TicketListItemResponse(
        Long id,
        String title,
        TicketStatus status,
        TicketPriority priority,
        Instant dueDate,
        Long projectId,
        String projectName,
        Long teamId,
        String teamName,
        Long assignedToId,
        String assignedToName,
        Long createdById,
        String createdByName,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
