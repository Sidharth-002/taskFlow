package com.flowdesk.notification.repository;

import com.flowdesk.notification.entity.Notification;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserIdAndOrganizationId(Long recipientUserId, Long organizationId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);
}
