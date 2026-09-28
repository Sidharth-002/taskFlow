package com.flowdesk.team.repository;

import com.flowdesk.team.entity.Team;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByIdAndOrganizationId(Long id, Long organizationId);

    Page<Team> findByOrganizationId(Long organizationId, Pageable pageable);
}
