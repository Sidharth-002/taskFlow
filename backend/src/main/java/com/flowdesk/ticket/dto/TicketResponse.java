package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import java.time.Instant;

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
