import { apiClient } from "../../shared/api/client";
import type { Page } from "../../shared/types";

export type NotificationType =
  | "TICKET_ASSIGNED"
  | "TICKET_STATUS_CHANGED"
  | "TICKET_CLOSED"
  | "TICKET_COMMENT_ADDED"
  | "TICKET_OVERDUE";

export interface NotificationResponse {
  id: number;
  ticketId: number;
  type: NotificationType;
  message: string;
  read: boolean;
  createdAt: string;
}

export function listNotifications(params: { page?: number; size?: number } = {}) {
  return apiClient.get<Page<NotificationResponse>>("/api/notifications", { params }).then((r) => r.data);
}

export function markNotificationRead(id: number) {
  return apiClient.patch<NotificationResponse>(`/api/notifications/${id}/read`).then((r) => r.data);
}
