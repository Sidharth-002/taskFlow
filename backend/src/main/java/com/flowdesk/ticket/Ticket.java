package com.flowdesk.ticket;

import com.flowdesk.common.BaseEntity;
import com.flowdesk.organization.Organization;
import com.flowdesk.project.Project;
import com.flowdesk.team.Team;
import com.flowdesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The core domain entity: a support/work ticket.
 *
 * <p><b>Denormalized {@code organization}:</b> a ticket is already
 * transitively scoped to an organization via {@code project.organization},
 * but {@code organization_id} is stored directly on the ticket row as
 * well. This is a deliberate denormalization, not an oversight: every
 * tenant-isolation check and most list/filter queries (Section 15/16 of
 * the spec - filter by org, status, priority, team, assignee, etc.) need
 * to scope by organization, and requiring a join through {@code projects}
 * just to apply a WHERE clause that exists on every single query would be
 * wasteful. The tradeoff is that {@code ticket.organization} and
 * {@code ticket.project.organization} must always agree - enforced in the
 * service layer when a ticket is created or its project is changed.
 *
 * <p><b>Optimistic locking:</b> {@code @Version} guards against lost
 * updates when two agents edit the same ticket concurrently (e.g. one
 * changes status while another reassigns it) - see the ticket workflow
 * and concurrency tests added in Phase 6.
 *
 * <p>All associations are {@link FetchType#LAZY}. Ticket lists are
 * paginated and filtered (Phase 6), and eagerly loading {@code project},
 * {@code team}, {@code createdBy} and {@code assignedTo} for every row in
 * a page would multiply query count for no benefit when, e.g., only the
 * title and status are being rendered - callers that need associated data
 * use a fetch join / entity graph / DTO projection deliberately, rather
 * than paying that cost on every load by default.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Column(name = "due_date")
    private Instant dueDate;

    /**
     * Optimistic lock. Hibernate increments this automatically on every
     * UPDATE and includes it in the WHERE clause; a concurrent write
     * based on a stale version raises
     * {@link jakarta.persistence.OptimisticLockException}, translated to
     * a domain-specific exception at the service boundary (Phase 6).
     */
    @Version
    @Column(nullable = false)
    private Long version;
}
