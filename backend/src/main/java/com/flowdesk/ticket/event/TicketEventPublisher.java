package com.flowdesk.ticket.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The only way {@code TicketService}/{@code CommentService} touch domain
 * events - they call {@link #publish} inside their existing
 * {@code @Transactional} method and never see Kafka at all. Publishing
 * goes through Spring's in-process {@link ApplicationEventPublisher}
 * first, not straight to Kafka, specifically so {@link TicketEventKafkaRelay}
 * can defer the actual Kafka send until the surrounding transaction
 * commits (see its Javadoc) - if this method sent to Kafka directly, a
 * ticket creation that fails and rolls back after this call would still
 * have told the world (via Kafka) that the ticket exists.
 *
 * <p>A thin wrapper around {@code ApplicationEventPublisher} rather than
 * services depending on it directly: it gives ticket/comment services a
 * narrowly-typed collaborator (only {@link TicketDomainEvent}s, not
 * arbitrary Spring {@code ApplicationEvent}s) and keeps the
 * publish-now-relay-after-commit design documented in one place.
 */
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
