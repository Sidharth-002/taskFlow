package com.flowdesk.ticket.event;

import java.time.Instant;

/**
 * Common shape for every ticket-lifecycle domain event (Phase 8):
 * {@link TicketCreatedEvent}, {@link TicketAssignedEvent},
 * {@link TicketStatusChangedEvent}, {@link TicketClosedEvent}, and
 * {@code comment.event.TicketCommentAddedEvent}. All five are published
 * to the same Kafka topic ({@link com.flowdesk.shared.messaging.KafkaTopics}) and
 * consumed generically by both {@code notification} and {@code audit} -
 * this interface is what lets a {@code @KafkaListener} method accept any
 * of them with one signature, with the concrete type resolved from the
 * JSON payload's embedded type header (see {@code application.yml}'s
 * Kafka consumer config).
 *
 * @param ticketId the ticket this event is about - also used as the
 *                 Kafka message key, so every event for the same ticket
 *                 lands in the same partition and is consumed in the
 *                 order it actually happened
 * @param organizationId included on every event so a consumer never has
 *                        to look the ticket up just to find out which
 *                        tenant it belongs to
 * @param occurredAt when the underlying change happened, not when a
 *                    consumer eventually processes it - the two can
 *                    differ under consumer lag
 */
public interface TicketDomainEvent {

    Long ticketId();

    Long organizationId();

    Instant occurredAt();
}
