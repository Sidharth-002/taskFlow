package com.flowdesk.ticket.mapper;

import com.flowdesk.ticket.dto.TicketListItemResponse;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.entity.Ticket;
import org.springframework.stereotype.Component;

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
