package com.flowdesk.audit.service;

import com.flowdesk.audit.dto.AuditLogResponse;
import com.flowdesk.audit.entity.AuditLog;
import com.flowdesk.audit.mapper.AuditLogMapper;
import com.flowdesk.audit.repository.AuditLogRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.ticket.service.TicketService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final TicketService ticketService;

    public AuditService(AuditLogRepository auditLogRepository, AuditLogMapper auditLogMapper, TicketService ticketService) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
        this.ticketService = ticketService;
    }

    @Transactional
    public void record(Long organizationId, Long ticketId, String eventType, Long actorUserId, String summary, Instant occurredAt) {
        auditLogRepository.save(AuditLog.builder()
                .organizationId(organizationId)
                .ticketId(ticketId)
                .eventType(eventType)
                .actorUserId(actorUserId)
                .summary(summary)
                .occurredAt(occurredAt)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listForTicket(Long ticketId, AuthenticatedPrincipal caller) {
        ticketService.loadVisible(ticketId, caller);
        return auditLogRepository.findByTicketIdOrderByOccurredAtAsc(ticketId).stream()
                .map(auditLogMapper::toResponse)
                .toList();
    }
}
