package com.flowdesk.project.dto;

import com.flowdesk.project.entity.ProjectStatus;
import java.time.Instant;

public record ProjectResponse(
        Long id,
        Long organizationId,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
