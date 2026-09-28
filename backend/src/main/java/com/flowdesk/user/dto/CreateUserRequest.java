package com.flowdesk.user.dto;

import com.flowdesk.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code ORG_ADMIN}-only: adds a colleague directly to the caller's own
 * organization. Unlike {@code auth.dto.RegisterRequest}, this never
 * creates a new organization - discovered as a hard requirement while
 * verifying Phase 4's ticket assignment/visibility rules, since without
 * it an organization can never have more than the one admin who
 * registered it, which makes "assign to an AGENT" or "TEAM_LEAD's view"
 * impossible to exercise at all.
 */
public record CreateUserRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$",
                message = "Password must be at least 8 characters and contain both letters and numbers")
        String password,

        @NotNull(message = "Role is required")
        Role role) {
}
