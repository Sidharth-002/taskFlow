package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;

public record TicketSearchCriteria(
        TicketStatus status,
        TicketPriority priority,
        Long projectId,
        Long teamId,
        Long assignedToId,
        String search) {
}
