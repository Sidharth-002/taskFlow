import { apiClient } from "./client";
import type { CommentResponse } from "../types";

export function listComments(ticketId: number) {
  return apiClient.get<CommentResponse[]>(`/api/tickets/${ticketId}/comments`).then((r) => r.data);
}

export function addComment(ticketId: number, body: string) {
  return apiClient.post<CommentResponse>(`/api/tickets/${ticketId}/comments`, { body }).then((r) => r.data);
}
