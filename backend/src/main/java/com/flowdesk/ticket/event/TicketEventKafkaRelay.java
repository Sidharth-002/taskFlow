package com.flowdesk.ticket.event;

import com.flowdesk.shared.messaging.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
                        log.error("Failed to publish {} for ticket {}", event.getClass().getSimpleName(), event.ticketId(), ex);
                    }
                });
    }
}
