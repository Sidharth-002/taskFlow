package com.flowdesk.ticket.spec;

import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import org.springframework.data.jpa.domain.Specification;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> inOrganization(Long organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

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

    public static Specification<Ticket> titleContains(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern);
    }
}
