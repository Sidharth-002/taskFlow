package com.flowdesk.audit.repository;

import com.flowdesk.audit.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTicketIdOrderByOccurredAtAsc(Long ticketId);
}
