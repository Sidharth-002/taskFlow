import { apiClient } from "../../shared/api/client";
import { ALL, type Page } from "../../shared/types";

export interface ProjectResponse {
  id: number;
  organizationId: number;
  name: string;
  description: string | null;
  status: "ACTIVE" | "ARCHIVED";
}

export interface ProjectInput {
  name: string;
  description?: string;
}

export function listProjects() {
  return apiClient
    .get<Page<ProjectResponse>>("/api/projects", { params: { size: ALL, sort: "name" } })
    .then((r) => r.data.content);
}

export function createProject(input: ProjectInput) {
  return apiClient.post<ProjectResponse>("/api/projects", input).then((r) => r.data);
}

export function updateProject(id: number, input: ProjectInput) {
  return apiClient.put<ProjectResponse>(`/api/projects/${id}`, input).then((r) => r.data);
}

export function archiveProject(id: number) {
  return apiClient.patch<ProjectResponse>(`/api/projects/${id}/archive`).then((r) => r.data);
}
