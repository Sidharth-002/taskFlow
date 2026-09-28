package com.flowdesk.team.dto;

import jakarta.validation.constraints.NotNull;

public record AssignTeamLeadRequest(@NotNull(message = "userId is required") Long userId) {
}
