package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateTicketRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 10_000, message = "Description must be at most 10,000 characters")
        String description,

        /** Defaults to {@link TicketPriority#MEDIUM} in the service if omitted. */
        TicketPriority priority,

        @NotNull(message = "Project is required")
        Long projectId,

        Long teamId,

        Long assignedToId,

        Instant dueDate) {
}
