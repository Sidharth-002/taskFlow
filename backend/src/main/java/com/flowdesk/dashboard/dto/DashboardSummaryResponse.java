package com.flowdesk.dashboard.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import java.util.Map;

public record DashboardSummaryResponse(
        long totalTickets,
        Map<TicketStatus, Long> countsByStatus,
        Map<TicketPriority, Long> countsByPriority,
        long unassignedCount,
        long overdueCount,
        long createdLastSevenDays,
        long closedLastSevenDays) {
}
