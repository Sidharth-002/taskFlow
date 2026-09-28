package com.flowdesk.user.dto;

import com.flowdesk.user.entity.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull(message = "role is required") Role role) {
}
