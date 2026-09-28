import { apiClient } from "../../shared/api/client";
import { ALL, type Page, type UserSummary } from "../../shared/types";

export interface TeamResponse {
  id: number;
  organizationId: number;
  name: string;
  description: string | null;
  teamLeadId: number | null;
  teamLeadName: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TeamInput {
  name: string;
  description?: string;
}

export function listTeams() {
  return apiClient
    .get<Page<TeamResponse>>("/api/teams", { params: { size: ALL, sort: "name" } })
    .then((r) => r.data.content);
}

export function createTeam(input: TeamInput) {
  return apiClient.post<TeamResponse>("/api/teams", input).then((r) => r.data);
}

export function updateTeam(id: number, input: TeamInput) {
  return apiClient.put<TeamResponse>(`/api/teams/${id}`, input).then((r) => r.data);
}

export function deactivateTeam(id: number) {
  return apiClient.patch<TeamResponse>(`/api/teams/${id}/deactivate`).then((r) => r.data);
}

export function assignTeamLead(id: number, userId: number) {
  return apiClient.patch<TeamResponse>(`/api/teams/${id}/lead`, { userId }).then((r) => r.data);
}

export function listTeamMembers(id: number) {
  return apiClient.get<UserSummary[]>(`/api/teams/${id}/members`).then((r) => r.data);
}

export function addTeamMember(id: number, userId: number) {
  return apiClient.post<UserSummary[]>(`/api/teams/${id}/members/${userId}`).then((r) => r.data);
}

export function removeTeamMember(id: number, userId: number) {
  return apiClient.delete<void>(`/api/teams/${id}/members/${userId}`);
}
