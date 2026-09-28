package com.flowdesk.notification.dto;

import com.flowdesk.notification.entity.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long ticketId,
        NotificationType type,
        String message,
        boolean read,
        Instant createdAt) {
}
