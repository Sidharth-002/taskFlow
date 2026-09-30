package com.flowdesk.security;

import com.flowdesk.user.entity.Role;

public record AuthenticatedPrincipal(Long userId, Long organizationId, String email, Role role) {
}
