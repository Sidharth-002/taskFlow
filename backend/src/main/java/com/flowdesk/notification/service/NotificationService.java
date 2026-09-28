package com.flowdesk.notification.service;

import com.flowdesk.shared.exception.ResourceNotFoundException;
import com.flowdesk.notification.dto.NotificationResponse;
import com.flowdesk.notification.entity.Notification;
import com.flowdesk.notification.entity.NotificationType;
import com.flowdesk.notification.mapper.NotificationMapper;
import com.flowdesk.notification.repository.NotificationRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link #create} is only ever called from {@code NotificationEventListener}
 * (a Kafka consumer, not a controller) - there is no
 * {@code POST /api/notifications}; a notification is always a side effect
 * of a ticket domain event, never something a client creates directly.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationRepository notificationRepository, NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    @Transactional
    public void create(Long organizationId, Long recipientUserId, Long ticketId, NotificationType type, String message) {
        notificationRepository.save(Notification.builder()
                .organizationId(organizationId)
                .recipientUserId(recipientUserId)
                .ticketId(ticketId)
                .type(type)
                .message(message)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Pageable pageable, AuthenticatedPrincipal caller) {
        return notificationRepository
                .findByRecipientUserIdAndOrganizationId(caller.userId(), caller.organizationId(), pageable)
                .map(notificationMapper::toResponse);
    }

    /**
     * A recipient may only mark their own notifications read - there is no
     * {@code ORG_ADMIN} override, unlike comment moderation, since another
     * user's notification inbox isn't something an admin has a legitimate
     * reason to modify.
     */
    @Transactional
    public NotificationResponse markRead(Long id, AuthenticatedPrincipal caller) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(id, caller.userId())
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        notification.setRead(true);
        return notificationMapper.toResponse(notificationRepository.saveAndFlush(notification));
    }
}
