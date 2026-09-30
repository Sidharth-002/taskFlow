package com.flowdesk.ticket.repository;

import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByIdAndOrganizationId(Long id, Long organizationId);

    @Override
    @EntityGraph(attributePaths = {"project", "team", "createdBy", "assignedTo"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    @Query("select t from Ticket t where t.dueDate < :now and t.status not in :excludedStatuses and t.overdueNotifiedAt is null")
    List<Ticket> findOverdueAndNotYetNotified(@Param("now") Instant now, @Param("excludedStatuses") Collection<TicketStatus> excludedStatuses);
}
