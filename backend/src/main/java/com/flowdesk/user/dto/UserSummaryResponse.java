package com.flowdesk.user.dto;

import com.flowdesk.user.entity.Role;

public record UserSummaryResponse(
        Long id,
        Long organizationId,
        String email,
        String firstName,
        String lastName,
        Role role,
        boolean active) {
}
