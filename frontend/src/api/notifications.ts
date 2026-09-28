import { apiClient } from "./client";
import type { NotificationResponse, Page } from "../types";

export function listNotifications(params: { page?: number; size?: number } = {}) {
  return apiClient.get<Page<NotificationResponse>>("/api/notifications", { params }).then((r) => r.data);
}

export function markNotificationRead(id: number) {
  return apiClient.patch<NotificationResponse>(`/api/notifications/${id}/read`).then((r) => r.data);
}
