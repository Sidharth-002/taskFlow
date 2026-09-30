import { apiClient } from "../../shared/api/client";
import type { TicketPriority, TicketStatus } from "../tickets/types";

export interface DashboardSummary {
  totalTickets: number;
  countsByStatus: Partial<Record<TicketStatus, number>>;
  countsByPriority: Partial<Record<TicketPriority, number>>;
  unassignedCount: number;
  overdueCount: number;
  createdLastSevenDays: number;
  closedLastSevenDays: number;
}

export function getDashboardSummary() {
  return apiClient.get<DashboardSummary>("/api/dashboard/summary").then((r) => r.data);
}
