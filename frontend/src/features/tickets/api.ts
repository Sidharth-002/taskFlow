import { apiClient } from "../../shared/api/client";
import type { Page } from "../../shared/types";
import type {
  AuditLogResponse,
  CommentResponse,
  TicketDetail,
  TicketFilters,
  TicketListItem,
  TicketPriority,
  TicketStatus,
} from "./types";

export function listTickets(params: TicketFilters & { page?: number; size?: number; sort?: string }) {
  return apiClient.get<Page<TicketListItem>>("/api/tickets", { params }).then((r) => r.data);
}

export function getTicket(id: number) {
  return apiClient.get<TicketDetail>(`/api/tickets/${id}`).then((r) => r.data);
}

export interface CreateTicketInput {
  title: string;
  description?: string;
  priority?: TicketPriority;
  projectId: number;
  teamId?: number;
  assignedToId?: number;
  dueDate?: string;
}

export function createTicket(input: CreateTicketInput) {
  return apiClient.post<TicketDetail>("/api/tickets", input).then((r) => r.data);
}

export function updateTicket(
  id: number,
  input: Partial<{
    title: string;
    description: string;
    status: TicketStatus;
    priority: TicketPriority;
    teamId: number;
    assignedToId: number;
    dueDate: string;
  }>,
) {
  return apiClient.patch<TicketDetail>(`/api/tickets/${id}`, input).then((r) => r.data);
}

export function deleteTicket(id: number) {
  return apiClient.delete<void>(`/api/tickets/${id}`);
}

export function listComments(ticketId: number) {
  return apiClient.get<CommentResponse[]>(`/api/tickets/${ticketId}/comments`).then((r) => r.data);
}

export function addComment(ticketId: number, body: string) {
  return apiClient.post<CommentResponse>(`/api/tickets/${ticketId}/comments`, { body }).then((r) => r.data);
}

export function updateComment(id: number, body: string) {
  return apiClient.patch<CommentResponse>(`/api/comments/${id}`, { body }).then((r) => r.data);
}

export function deleteComment(id: number) {
  return apiClient.delete<void>(`/api/comments/${id}`);
}

export function listAuditLog(ticketId: number) {
  return apiClient.get<AuditLogResponse[]>(`/api/tickets/${ticketId}/audit-log`).then((r) => r.data);
}
