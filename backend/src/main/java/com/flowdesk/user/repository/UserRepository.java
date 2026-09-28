package com.flowdesk.user.repository;

import com.flowdesk.user.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndOrganizationId(Long id, Long organizationId);

    Page<User> findByOrganizationId(Long organizationId, Pageable pageable);
}
