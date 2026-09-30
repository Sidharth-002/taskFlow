package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import java.time.Instant;

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
