package com.flowdesk.ticket.mapper;

import com.flowdesk.ticket.dto.TicketListItemResponse;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.entity.Ticket;
import org.springframework.stereotype.Component;

/**
 * {@link #toResponse} maps only association <em>IDs</em>, never names -
 * {@code getId()} on a lazy {@code @ManyToOne} proxy is answered from the
 * proxy's own identifier without touching the database (that's the whole
 * point of a proxy), so it's N+1-safe by construction even against an
 * unfetched entity. Used for single-ticket reads
 * ({@code TicketService.getById}/{@code create}/{@code update}), where
 * only one row is ever mapped per request.
 *
 * <p>{@link #toListItem} does call e.g. {@code getProject().getName()},
 * which would initialize a lazy proxy and cost one query per association
 * per row if used carelessly - it's only safe because
 * {@code TicketRepository.findAll(Specification, Pageable)} fetch-joins
 * those associations up front (see its Javadoc), so every association is
 * already initialized by the time a row reaches this mapper.
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

    public TicketListItemResponse toListItem(Ticket ticket) {
        return new TicketListItemResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getDueDate(),
                ticket.getProject().getId(),
                ticket.getProject().getName(),
                ticket.getTeam() != null ? ticket.getTeam().getId() : null,
                ticket.getTeam() != null ? ticket.getTeam().getName() : null,
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null,
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getFullName() : null,
                ticket.getCreatedBy().getId(),
                ticket.getCreatedBy().getFullName(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
