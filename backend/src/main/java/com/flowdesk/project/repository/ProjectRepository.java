package com.flowdesk.project.repository;

import com.flowdesk.project.entity.Project;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndOrganizationId(Long id, Long organizationId);

    Page<Project> findByOrganizationId(Long organizationId, Pageable pageable);
}
