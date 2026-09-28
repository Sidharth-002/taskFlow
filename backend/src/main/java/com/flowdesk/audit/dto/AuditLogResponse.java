package com.flowdesk.audit.dto;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long ticketId,
        String eventType,
        Long actorUserId,
        String summary,
        Instant occurredAt) {
}
