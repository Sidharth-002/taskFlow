package com.flowdesk.user.entity;

import com.flowdesk.shared.persistence.BaseEntity;
import com.flowdesk.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A FlowDesk account.
 *
 * <p>
 * Every user belongs to exactly one organization, with one exception:
 * {@link Role#SUPER_ADMIN} accounts are platform-level (they manage
 * organizations themselves, per the role's permissions) and are not
 * scoped to any single tenant, so {@code organization} is nullable and
 * populated for every role except {@code SUPER_ADMIN}.
 *
 * <p>
 * Email is unique platform-wide (not just per-organization), which
 * keeps login simple: a user is looked up by email alone, and their
 * organization is derived from the matched account rather than needing
 * to be supplied separately at login time.
 *
 * <p>
 * The {@code organization} association is {@link FetchType#LAZY} -
 * loading a user should not implicitly pull the organization row unless
 * it's actually needed, which matters once user lists are paginated
 * (see the N+1 prevention notes in the README once ticket/user listing
 * endpoints are built).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
