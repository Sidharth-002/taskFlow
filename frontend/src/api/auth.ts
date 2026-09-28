import { apiClient } from "./client";
import type { AuthResponse, UserSummary } from "../types";

export function register(input: {
  organizationName: string;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}) {
  return apiClient.post<AuthResponse>("/api/auth/register", input).then((r) => r.data);
}

export function login(input: { email: string; password: string }) {
  return apiClient.post<AuthResponse>("/api/auth/login", input).then((r) => r.data);
}

export function logout(refreshToken: string) {
  return apiClient.post<void>("/api/auth/logout", { refreshToken });
}

export function getCurrentUser() {
  return apiClient.get<UserSummary>("/api/auth/me").then((r) => r.data);
}
