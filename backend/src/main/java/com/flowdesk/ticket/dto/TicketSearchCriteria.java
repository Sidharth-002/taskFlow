package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;

/**
 * User-supplied ticket list filters, all optional. Bound directly from
 * {@code @RequestParam}s in {@code TicketController} rather than a request
 * body, since this is a {@code GET}. Every field left {@code null} is
 * treated as "don't filter on this" - see {@code TicketSpecifications}.
 */
public record TicketSearchCriteria(
        TicketStatus status,
        TicketPriority priority,
        Long projectId,
        Long teamId,
        Long assignedToId,
        String search) {
}
