package com.flowdesk.team.dto;

import java.time.Instant;

public record TeamResponse(
        Long id,
        Long organizationId,
        String name,
        String description,
        Long teamLeadId,
        String teamLeadName,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
