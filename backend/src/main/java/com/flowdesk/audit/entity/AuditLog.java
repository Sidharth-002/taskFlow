package com.flowdesk.audit.entity;

import com.flowdesk.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One immutable row per ticket domain event - the permanent activity
 * history for a ticket, written by {@code AuditEventListener} and never
 * updated or deleted by application code afterward.
 *
 * <p>Plain columns rather than {@code @ManyToOne} associations, same
 * reasoning as {@code Notification}: this is a log record, not a live
 * relationship, and {@code ticketId} in particular must survive the
 * ticket itself being deleted (see the migration's comment).
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
