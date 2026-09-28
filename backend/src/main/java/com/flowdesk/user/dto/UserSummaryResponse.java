package com.flowdesk.user.dto;

import com.flowdesk.user.entity.Role;

/**
 * The public-facing view of a {@code User}. Never includes
 * {@code passwordHash} or anything else internal - see
 * {@code user.mapper.UserMapper}.
 */
public record UserSummaryResponse(
        Long id,
        Long organizationId,
        String email,
        String firstName,
        String lastName,
        Role role) {
}
