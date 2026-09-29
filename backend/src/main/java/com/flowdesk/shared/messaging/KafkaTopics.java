package com.flowdesk.config;

/**
 * Every ticket domain event (see {@code ticket.event}) is published to
 * this one topic, discriminated by type rather than split across one
 * topic per event type. Both consumers ({@code notification} and
 * {@code audit}) need to see every event kind, so splitting into multiple
 * topics would only mean subscribing to all of them anyway, for no
 * ordering or scaling benefit at this project's volume - a single topic
 * keyed by ticket ID also keeps every event for the same ticket in the
 * same partition, so a consumer sees them in the order they actually
 * happened.
 */
public final class KafkaTopics {

    public static final String TICKET_EVENTS = "ticket-events";

    private KafkaTopics() {
    }
}
