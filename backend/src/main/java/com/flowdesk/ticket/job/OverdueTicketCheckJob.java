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

@Component
public class OverdueTicketCheckJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueTicketCheckJob.class);

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
            ticket.setOverdueNotifiedAt(now);
        }

        if (!overdue.isEmpty()) {
            log.info("Published TicketOverdueEvent for {} newly-overdue ticket(s)", overdue.size());
        }
    }
}
