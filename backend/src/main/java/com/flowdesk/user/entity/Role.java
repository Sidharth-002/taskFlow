package com.flowdesk.user.entity;

/**
 * The finite set of roles a user can hold within their organization.
 *
 * <p>Roles are broadly ordered from most to least privileged, though
 * authorization checks (see {@code security} module, Phase 3) are
 * expressed per-permission via {@code @PreAuthorize}, not via a numeric
 * hierarchy comparison, since e.g. AGENT and USER have overlapping but
 * distinct permissions rather than one being a strict subset of the other.
 *
 * <ul>
 *   <li>{@link #SUPER_ADMIN} - manages organizations and organization admins; not scoped to a single organization</li>
 *   <li>{@link #ORG_ADMIN} - manages users, teams, projects and tickets within their organization</li>
 *   <li>{@link #TEAM_LEAD} - manages their team's members and assigns/updates tickets for their team</li>
 *   <li>{@link #AGENT} - works assigned tickets: updates status, comments</li>
 *   <li>{@link #USER} - creates tickets and tracks their own</li>
 * </ul>
 */
public enum Role {
    SUPER_ADMIN,
    ORG_ADMIN,
    TEAM_LEAD,
    AGENT,
    USER
}
