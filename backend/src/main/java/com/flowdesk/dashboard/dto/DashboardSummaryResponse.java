package com.flowdesk.dashboard.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import java.util.Map;

/**
 * Scoped to whatever set of tickets is visible to the caller
 * (org-wide for {@code ORG_ADMIN}, their own team's for {@code TEAM_LEAD}
 * - see {@code DashboardService}), never every ticket in the database
 * regardless of caller.
 *
 * @param totalTickets           total count within scope
 * @param countsByStatus         only statuses with at least one ticket are present - a
 *                               status with zero tickets is simply absent from the map,
 *                               not present with a {@code 0} value
 * @param countsByPriority       same "absent means zero" convention as {@code countsByStatus}
 * @param unassignedCount        tickets with no {@code assignedTo}
 * @param overdueCount           {@code dueDate} in the past and not {@code RESOLVED}/{@code CLOSED} -
 *                               the same definition {@code OverdueTicketCheckJob} uses
 * @param createdLastSevenDays   tickets whose {@code createdAt} falls in the last 7 days
 * @param closedLastSevenDays    tickets currently {@code CLOSED} whose {@code updatedAt} falls in the
 *                               last 7 days - an approximation, not a dedicated {@code closedAt}
 *                               timestamp (see {@code DashboardService}'s Javadoc for the edge case
 *                               this can miss)
 */
public record DashboardSummaryResponse(
        long totalTickets,
        Map<TicketStatus, Long> countsByStatus,
        Map<TicketPriority, Long> countsByPriority,
        long unassignedCount,
        long overdueCount,
        long createdLastSevenDays,
        long closedLastSevenDays) {
}
