package com.flowdesk.organization.entity;

import com.flowdesk.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tenant in FlowDesk's shared-schema multi-tenant model.
 *
 * <p>Every organization-owned entity (users, teams, projects, tickets)
 * carries an {@code organization_id} foreign key back to this table, and
 * that column is the enforcement point for tenant isolation at the
 * repository/service layer (see the multi-tenancy section of the README).
 *
 * <p>Organizations are deactivated rather than hard-deleted, so that
 * historical data (tickets, audit trails) referencing the organization
 * remains intact.
 */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
