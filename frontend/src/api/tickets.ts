import { apiClient } from "./client";
import type { Page, TicketDetail, TicketListItem, TicketStatus } from "../types";

export function listTickets(params: { status?: TicketStatus; page?: number; size?: number }) {
  return apiClient.get<Page<TicketListItem>>("/api/tickets", { params }).then((r) => r.data);
}

export function getTicket(id: number) {
  return apiClient.get<TicketDetail>(`/api/tickets/${id}`).then((r) => r.data);
}

export function createTicket(input: {
  title: string;
  description?: string;
  priority?: string;
  projectId: number;
  dueDate?: string;
}) {
  return apiClient.post<TicketDetail>("/api/tickets", input).then((r) => r.data);
}

export function updateTicket(
  id: number,
  input: Partial<{
    title: string;
    description: string;
    status: TicketStatus;
    priority: string;
    dueDate: string;
  }>,
) {
  return apiClient.patch<TicketDetail>(`/api/tickets/${id}`, input).then((r) => r.data);
}
