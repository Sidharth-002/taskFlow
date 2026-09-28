package com.flowdesk.ticket.repository;

import com.flowdesk.ticket.entity.Ticket;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * One fixed query per role's default visibility scope (Section 6 of the
 * spec - a plain {@code USER} sees only tickets they created, an
 * {@code AGENT} only ones assigned to them, etc; see
 * {@code TicketService.list}). Combining these with <em>user-supplied</em>
 * filters (status/priority/search) and fixing the N+1 risk of a paginated
 * list is Phase 6's job, via {@code JpaSpecificationExecutor} and a
 * fetch-join/entity-graph/projection - this interface intentionally stays
 * simple until then.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByIdAndOrganizationId(Long id, Long organizationId);

    Page<Ticket> findByOrganizationId(Long organizationId, Pageable pageable);

    Page<Ticket> findByOrganizationIdAndCreatedById(Long organizationId, Long createdById, Pageable pageable);

    Page<Ticket> findByOrganizationIdAndAssignedToId(Long organizationId, Long assignedToId, Pageable pageable);

    Page<Ticket> findByOrganizationIdAndTeamTeamLeadId(Long organizationId, Long teamLeadId, Pageable pageable);
}
