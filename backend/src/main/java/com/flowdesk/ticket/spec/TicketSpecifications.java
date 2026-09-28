package com.flowdesk.ticket.spec;

import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic ticket search, built one composable {@link Specification} per
 * concern and combined with {@code Specification.where(...).and(...)}
 * rather than one hand-written JPQL string per role/filter combination.
 * {@code Specification.where(null).and(null)} is well-defined (both sides
 * degrade to "no restriction"), so every method here simply returns
 * {@code null} for "this filter wasn't supplied" and the caller chains them
 * unconditionally - see {@code TicketService.list}.
 *
 * <p>Replaces the four fixed per-role repository query methods
 * {@code TicketRepository} used through Phase 5: a plain user-supplied
 * filter (status/priority/project/team/assignee/search) now composes with
 * whichever role-visibility restriction applies, instead of only being
 * usable against one fixed query per role.
 */
public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> inOrganization(Long organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    /**
     * The same default-visibility rule per role as Phase 4/5's fixed
     * queries (Section 6 of the spec), just expressed as a composable
     * predicate instead of a whole separate query method.
     * {@code SUPER_ADMIN} is unreachable here ({@code TicketController}'s
     * {@code @PreAuthorize} excludes it) but returns a deny-all predicate
     * rather than silently falling through to "no restriction" if that
     * ever changes.
     */
    public static Specification<Ticket> visibleTo(AuthenticatedPrincipal caller) {
        return switch (caller.role()) {
            case ORG_ADMIN -> null;
            case TEAM_LEAD -> (root, query, cb) ->
                    cb.equal(root.get("team").get("teamLead").get("id"), caller.userId());
            case AGENT -> (root, query, cb) -> cb.equal(root.get("assignedTo").get("id"), caller.userId());
            case USER -> (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), caller.userId());
            case SUPER_ADMIN -> (root, query, cb) -> cb.disjunction();
        };
    }

    public static Specification<Ticket> hasStatus(TicketStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Ticket> hasPriority(TicketPriority priority) {
        return priority == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Ticket> hasProjectId(Long projectId) {
        return projectId == null ? null : (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Ticket> hasTeamId(Long teamId) {
        return teamId == null ? null : (root, query, cb) -> cb.equal(root.get("team").get("id"), teamId);
    }

    public static Specification<Ticket> hasAssignedToId(Long assignedToId) {
        return assignedToId == null ? null
                : (root, query, cb) -> cb.equal(root.get("assignedTo").get("id"), assignedToId);
    }

    /** Case-insensitive substring match against the title, not full-text search. */
    public static Specification<Ticket> titleContains(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern);
    }
}
