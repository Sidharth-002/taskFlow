import { apiClient } from "../../shared/api/client";
import { ALL, type Page, type Role, type UserSummary } from "../../shared/types";

export interface CreateUserInput {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  role: Role;
}

export function listUsers() {
  return apiClient
    .get<Page<UserSummary>>("/api/users", { params: { size: ALL, sort: "firstName" } })
    .then((r) => r.data.content);
}

export function createUser(input: CreateUserInput) {
  return apiClient.post<UserSummary>("/api/users", input).then((r) => r.data);
}

export function setUserActive(id: number, active: boolean) {
  return apiClient.patch<UserSummary>(`/api/users/${id}/active`, { active }).then((r) => r.data);
}

export function changeUserRole(id: number, role: Role) {
  return apiClient.patch<UserSummary>(`/api/users/${id}/role`, { role }).then((r) => r.data);
}
