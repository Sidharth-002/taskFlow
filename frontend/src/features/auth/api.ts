import { apiClient } from "../../shared/api/client";
import type { UserSummary } from "../../shared/types";

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface RegisterInput {
  organizationName: string;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export function register(input: RegisterInput) {
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
