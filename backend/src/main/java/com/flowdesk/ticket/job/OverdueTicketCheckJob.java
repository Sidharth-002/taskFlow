package com.flowdesk.ticket.job;

import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.event.TicketEventPublisher;
import com.flowdesk.ticket.event.TicketOverdueEvent;
import com.flowdesk.ticket.repository.TicketRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds tickets whose {@code dueDate} has passed while they're still open
 * and publishes a {@link TicketOverdueEvent} for each - picked up by the
 * same {@code NotificationEventListener}/{@code AuditEventListener} that
 * handle every other ticket domain event (Phase 8), rather than this job
 * writing notifications/audit rows itself. Runs on
 * {@code app.scheduling.overdue-ticket-check-cron} (hourly by default -
 * see {@code application.yml}).
 *
 * <p>{@code run()} is a plain public method, called by {@code @Scheduled}
 * but also callable directly - tests invoke it directly rather than
 * waiting on a real cron trigger, the same reasoning
 * {@code AuthService.revokeAllTokensForUser} being independently callable
 * follows.
 */
@Component
public class OverdueTicketCheckJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueTicketCheckJob.class);

    /** A ticket in either of these terminal-for-this-purpose states is never "overdue" regardless of its due date. */
    private static final List<TicketStatus> EXCLUDED_STATUSES = List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final TicketRepository ticketRepository;
    private final TicketEventPublisher eventPublisher;

    public OverdueTicketCheckJob(TicketRepository ticketRepository, TicketEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "${app.scheduling.overdue-ticket-check-cron}")
    @Transactional
    public void run() {
        Instant now = Instant.now();
        List<Ticket> overdue = ticketRepository.findOverdueAndNotYetNotified(now, EXCLUDED_STATUSES);

        for (Ticket ticket : overdue) {
            eventPublisher.publish(new TicketOverdueEvent(
                    ticket.getId(), ticket.getOrganization().getId(), ticket.getDueDate(), now));
            // No explicit save: `ticket` is a managed entity within this
            // transaction (loaded by the repository query above), so
            // Hibernate's dirty checking persists this at flush/commit
            // time - the same pattern used throughout the service layer.
            ticket.setOverdueNotifiedAt(now);
        }

        if (!overdue.isEmpty()) {
            log.info("Published TicketOverdueEvent for {} newly-overdue ticket(s)", overdue.size());
        }
    }
}
