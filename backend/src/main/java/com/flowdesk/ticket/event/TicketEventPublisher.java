package com.flowdesk.ticket.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class TicketEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public TicketEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publish(TicketDomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
