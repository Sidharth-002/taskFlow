package com.flowdesk.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-service sign-up: provisions a brand-new organization
 * (tenant) with the caller as its first user, in the {@code ORG_ADMIN}
 * role. There is deliberately no way to register into an <em>existing</em>
 * organization here - once RBAC exists (Phase 5), an ORG_ADMIN adds
 * further users to their organization directly via the user management
 * API, rather than those users self-registering.
 */
public record RegisterRequest(

        @NotBlank(message = "Organization name is required")
        @Size(max = 255, message = "Organization name must be at most 255 characters")
        String organizationName,

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
        String password) {
}
