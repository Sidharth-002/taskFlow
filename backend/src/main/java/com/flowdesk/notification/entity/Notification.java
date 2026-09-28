package com.flowdesk.notification.entity;

import com.flowdesk.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An in-app notification for one recipient, created by
 * {@code NotificationEventListener} in reaction to a Kafka
 * {@code TicketDomainEvent} - never written directly by a controller.
 *
 * <p>{@code organizationId}/{@code recipientUserId}/{@code ticketId} are
 * plain columns, not {@code @ManyToOne} associations - this is a log-like
 * record of something that already happened, not a live relationship that
 * needs traversing, and denormalizing avoids a join (or a broken
 * reference, for {@code ticketId} - see the migration's comment) on every
 * read of "my notifications".
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;
}
