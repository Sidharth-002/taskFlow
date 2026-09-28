package com.flowdesk.audit.mapper;

import com.flowdesk.audit.dto.AuditLogResponse;
import com.flowdesk.audit.entity.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getTicketId(),
                auditLog.getEventType(),
                auditLog.getActorUserId(),
                auditLog.getSummary(),
                auditLog.getOccurredAt());
    }
}
