package com.flowdesk.ticket.mapper;

import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.entity.Ticket;
import org.springframework.stereotype.Component;

/**
 * Maps only association <em>IDs</em>, never names - {@code getId()} on a
 * lazy {@code @ManyToOne} proxy is answered from the proxy's own
 * identifier without touching the database (that's the whole point of a
 * proxy), so this mapper is N+1-safe by construction even when called
 * once per row in a paginated list. Calling e.g. {@code getProject().getName()}
 * instead would initialize the proxy and issue a query per row - exactly
 * the N+1 pattern Phase 6 addresses properly (via a fetch join / entity
 * graph / DTO projection) once list responses need to show names instead
 * of bare IDs.
 */
@Component
public class TicketMapper {

    public TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getOrganization().getId(),
                ticket.getProject().getId(),
                ticket.getTeam() != null ? ticket.getTeam().getId() : null,
                ticket.getCreatedBy().getId(),
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null,
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getDueDate(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
