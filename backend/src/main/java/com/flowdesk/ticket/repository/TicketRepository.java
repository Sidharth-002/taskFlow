package com.flowdesk.ticket.repository;

import com.flowdesk.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Dynamic filtering (status/priority/team/assignee/search), pagination and
 * N+1-safe fetching are added in Phase 6 via
 * {@code JpaSpecificationExecutor} and targeted fetch-join queries. This
 * interface is intentionally minimal for now.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
