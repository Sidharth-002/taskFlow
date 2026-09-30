package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateTicketRequest(

        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 10_000, message = "Description must be at most 10,000 characters")
        String description,

        TicketStatus status,

        TicketPriority priority,

        Long teamId,

        Long assignedToId,

        Instant dueDate) {
}
