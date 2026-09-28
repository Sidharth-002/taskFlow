package com.flowdesk.ticket.event;

import com.flowdesk.config.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays every {@link TicketDomainEvent} published in-process (via
 * {@link TicketEventPublisher}) to Kafka - the only class in this
 * application that actually touches {@link KafkaTemplate} for ticket
 * events.
 *
 * <p>{@code phase = AFTER_COMMIT} is the entire point of the two-step
 * publish-then-relay design ({@link TicketEventPublisher}): a domain event
 * raised inside a {@code @Transactional} service method is held until that
 * transaction actually commits, so a request that fails and rolls back
 * (e.g. an {@code ObjectOptimisticLockingFailureException} elsewhere in
 * the same method) never produces a Kafka message for a change that never
 * really happened. This is a deliberately lighter-weight alternative to a
 * full transactional outbox table - it closes the common case (the
 * in-process publish and the DB write share one transaction, so they
 * either both happen or neither does) but not the rarer one where the
 * transaction commits successfully and the process crashes before this
 * listener's Kafka send completes; a genuine outbox (writing the event to
 * a DB table in the same transaction, with a separate poller publishing
 * it to Kafka) would close that gap too, at the cost of that extra
 * table/poller, which isn't justified at this project's scale.
 *
 * <p>{@code fallbackExecution = true} makes this listener still fire when
 * there is no transaction in progress at all (e.g. a unit test invoking a
 * service method directly, or - in principle - a future call site that
 * isn't wrapped in {@code @Transactional}) rather than silently dropping
 * the event, since {@code @TransactionalEventListener}'s default behavior
 * is to skip execution entirely outside a transaction.
 */
@Component
public class TicketEventKafkaRelay {

    private static final Logger log = LoggerFactory.getLogger(TicketEventKafkaRelay.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TicketEventKafkaRelay(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(TicketDomainEvent event) {
        String key = String.valueOf(event.ticketId());
        kafkaTemplate.send(KafkaTopics.TICKET_EVENTS, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Logged, not rethrown: relaying is best-effort by
                        // design here (see the class Javadoc's outbox
                        // discussion) - a Kafka outage must not fail or
                        // retry the original HTTP request, which has
                        // already committed successfully by this point.
                        log.error("Failed to publish {} for ticket {}", event.getClass().getSimpleName(), event.ticketId(), ex);
                    }
                });
    }
}
