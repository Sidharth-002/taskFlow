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
