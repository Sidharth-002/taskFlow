package com.flowdesk.dashboard.service;

import static com.flowdesk.config.CacheConfig.DASHBOARD_CACHE;

import com.flowdesk.dashboard.dto.DashboardSummaryResponse;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.ticket.spec.TicketSpecifications;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregated reporting, scoped by the same role-based visibility rule as
 * {@code TicketService.list} (via {@code TicketSpecifications.visibleTo}) -
 * an {@code ORG_ADMIN} sees the whole organization, a {@code TEAM_LEAD}
 * sees only their own team's tickets. {@code DashboardController} excludes
 * {@code AGENT}/{@code USER} entirely: aggregated reporting is a
 * managerial concern in this design, not something every role needs a
 * view of.
 *
 * <p><b>Computed in application code, not SQL {@code GROUP BY}.</b> This
 * fetches every ticket in scope as entities (cheap here: no fetch join is
 * needed since only plain columns and lazy-proxy IDs are touched, so it's
 * a single-table scan, not N+1) and aggregates with Java streams, rather
 * than issuing one grouped-count query per breakdown. That trade-off is
 * only reasonable because of the caching below - without it, a dashboard
 * view would re-scan every ticket in scope on every request. A
 * significantly larger organization would eventually need a real
 * pre-aggregated reporting table instead; that's a deliberate
 * "not yet" for this project's scale, not an oversight.
 *
 * <p><b>Caching.</b> {@code DASHBOARD_CACHE} has its own, much shorter TTL
 * than the entity caches from Phase 7 (see {@code CacheConfig}) and is
 * never evicted by a ticket mutation - a dashboard is a snapshot by
 * nature, and a bounded staleness window is a simpler, acceptable
 * trade-off against wiring eviction into every ticket write path for a
 * value nobody expects to be exactly real-time. The cache key includes
 * the caller's role and, for {@code TEAM_LEAD}, their own user ID -
 * without that, two different team leads in the same organization would
 * incorrectly share one cached (and wrong, for at least one of them)
 * summary.
 *
 * <p><b>{@code closedLastSevenDays}'s known imprecision:</b> it's derived
 * from {@code updatedAt}, not a dedicated {@code closedAt} timestamp,
 * because none exists. This is normally accurate - {@code CLOSED} is a
 * terminal workflow state, so a closed ticket's status never changes
 * again - but {@code TicketService.update} doesn't currently forbid
 * editing a closed ticket's other fields (title, description, ...), so an
 * edited-after-closing ticket would show a later {@code updatedAt} than
 * its actual close time. Acceptable imprecision for a summary count, not
 * a genuine bug fix this phase takes on.
 */
@Service
public class DashboardService {

    private static final List<TicketStatus> OVERDUE_EXCLUDED_STATUSES = List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final TicketRepository ticketRepository;

    public DashboardService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Cacheable(cacheNames = DASHBOARD_CACHE,
            key = "#caller.organizationId() + ':' + (#caller.role().name() == 'TEAM_LEAD' ? 'tl' + #caller.userId() : 'org')")
    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(AuthenticatedPrincipal caller) {
        Specification<Ticket> spec = Specification.allOf(
                Stream.of(TicketSpecifications.inOrganization(caller.organizationId()), TicketSpecifications.visibleTo(caller))
                        .filter(Objects::nonNull)
                        .toList());
        List<Ticket> tickets = ticketRepository.findAll(spec);

        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);

        return new DashboardSummaryResponse(
                tickets.size(),
                groupAndCount(tickets, Ticket::getStatus),
                groupAndCount(tickets, Ticket::getPriority),
                tickets.stream().filter(t -> t.getAssignedTo() == null).count(),
                tickets.stream().filter(t -> isOverdue(t, now)).count(),
                tickets.stream().filter(t -> t.getCreatedAt().isAfter(sevenDaysAgo)).count(),
                tickets.stream().filter(t -> t.getStatus() == TicketStatus.CLOSED && t.getUpdatedAt().isAfter(sevenDaysAgo)).count());
    }

    private boolean isOverdue(Ticket ticket, Instant now) {
        return ticket.getDueDate() != null
                && ticket.getDueDate().isBefore(now)
                && !OVERDUE_EXCLUDED_STATUSES.contains(ticket.getStatus());
    }

    private <K> Map<K, Long> groupAndCount(List<Ticket> tickets, Function<Ticket, K> classifier) {
        return tickets.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }
}
