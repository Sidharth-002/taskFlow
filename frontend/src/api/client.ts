import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from "axios";
import { tokenStorage } from "./tokenStorage";

const baseURL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const apiClient = axios.create({ baseURL });

apiClient.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

const AUTH_PATHS_EXCLUDED_FROM_REFRESH = ["/api/auth/login", "/api/auth/refresh", "/api/auth/register"];

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retriedAfterRefresh?: boolean;
}

let refreshInFlight: Promise<string> | null = null;

function refreshAccessToken(): Promise<string> {
  if (!refreshInFlight) {
    const refreshToken = tokenStorage.getRefreshToken();
    if (!refreshToken) {
      return Promise.reject(new Error("No refresh token available"));
    }
    refreshInFlight = axios
      .post(`${baseURL}/api/auth/refresh`, { refreshToken })
      .then((response) => {
        const { accessToken, refreshToken: newRefreshToken } = response.data;
        tokenStorage.setTokens(accessToken, newRefreshToken);
        return accessToken as string;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as RetriableRequestConfig | undefined;
    const status = error.response?.status;
    const path = config?.url ?? "";
    const isExcluded = AUTH_PATHS_EXCLUDED_FROM_REFRESH.some((p) => path.includes(p));

    if (status === 401 && config && !config._retriedAfterRefresh && !isExcluded) {
      config._retriedAfterRefresh = true;
      try {
        const newAccessToken = await refreshAccessToken();
        config.headers = config.headers ?? {};
        (config.headers as Record<string, string>).Authorization = `Bearer ${newAccessToken}`;
        return apiClient.request(config as AxiosRequestConfig);
      } catch {
        tokenStorage.clear();
        window.location.href = "/login";
        return Promise.reject(error);
      }
    }

    return Promise.reject(error);
  },
);

export function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { message?: string } | undefined;
    if (body?.message) {
      return body.message;
    }
    if (error.response) {
      return `Request failed (${error.response.status})`;
    }
    return "Network error - is the API reachable?";
  }
  return "An unexpected error occurred";
}
