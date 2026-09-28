package com.flowdesk.ticket.repository;

import com.flowdesk.ticket.entity.Ticket;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * {@code JpaSpecificationExecutor} backs {@code TicketService.list}'s
 * dynamic search (role-visibility + user-supplied filters composed via
 * {@code TicketSpecifications}, see its Javadoc) in place of Phase 4/5's
 * one fixed query method per role.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByIdAndOrganizationId(Long id, Long organizationId);

    /**
     * Overrides {@code JpaSpecificationExecutor}'s default so that list
     * queries fetch {@code project}/{@code team}/{@code createdBy}/
     * {@code assignedTo} in the same round trip instead of one lazy load
     * per row per association (the classic N+1 for a paginated list that
     * renders names, not just IDs - see {@code TicketListItemResponse}).
     * Safe with pagination here specifically because every fetched
     * association is {@code @ManyToOne}: unlike fetching a
     * {@code @OneToMany}/{@code @ManyToMany} collection, this cannot
     * multiply row count, so in-memory pagination workarounds aren't
     * needed. Spring Data also automatically strips the entity graph from
     * the accompanying {@code COUNT} query, so the total-element count
     * isn't affected either.
     */
    @Override
    @EntityGraph(attributePaths = {"project", "team", "createdBy", "assignedTo"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);
}
