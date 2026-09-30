package com.flowdesk.ticket.event;

import java.time.Instant;

public interface TicketDomainEvent {

    Long ticketId();

    Long organizationId();

    Instant occurredAt();
}
