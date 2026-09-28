import { apiClient } from "./client";
import type { Page, ProjectResponse } from "../types";

export function listProjects() {
  return apiClient.get<Page<ProjectResponse>>("/api/projects", { params: { size: 100 } }).then((r) => r.data);
}

export function createProject(input: { name: string; description?: string }) {
  return apiClient.post<ProjectResponse>("/api/projects", input).then((r) => r.data);
}
