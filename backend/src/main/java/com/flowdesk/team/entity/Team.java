package com.flowdesk.team.entity;

import com.flowdesk.shared.persistence.BaseEntity;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A team within an organization.
 *
 * <p>Membership is a plain many-to-many (a user can belong to more than
 * one team, matching the doc's "users ... can belong to teams" wording)
 * backed by a {@code team_members} join table. There's no need for a
 * dedicated join entity here - membership carries no attributes of its
 * own (no joined-at timestamp, no per-team role) - so a join table
 * mapped via {@code @ManyToMany} keeps this simple rather than
 * introducing a {@code TeamMember} entity purely for its own sake.
 *
 * <p>{@code members} is the owning side and is intentionally
 * unidirectional (no {@code teams} collection back on {@link User}):
 * "teams for a given user" is a query concern (join from the
 * {@code team_members} table), not something every {@code User} load
 * needs to carry.
 */
@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_lead_id")
    private User teamLead;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "team_members",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @Builder.Default
    private Set<User> members = new HashSet<>();

    public void addMember(User user) {
        members.add(user);
    }

    public void removeMember(User user) {
        members.remove(user);
    }
}
