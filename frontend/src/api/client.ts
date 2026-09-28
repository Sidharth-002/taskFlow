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

// Paths that must never trigger the refresh-and-retry dance below, even on
// a 401 - refresh/login failing with 401 means the credential itself was
// rejected, not that the access token expired mid-session.
const AUTH_PATHS_EXCLUDED_FROM_REFRESH = ["/api/auth/login", "/api/auth/refresh", "/api/auth/register"];

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retriedAfterRefresh?: boolean;
}

let refreshInFlight: Promise<string> | null = null;

/**
 * A single refresh token is one-time-use (Phase 3's rotation - see the
 * backend's `RefreshToken` Javadoc), so two requests hitting a 401 at
 * the same moment must not each independently call `/api/auth/refresh`:
 * whichever loses that race would present an already-rotated-away token
 * and fail. `refreshInFlight` makes every 401 arriving while a refresh is
 * already in progress await that same promise instead of starting its own.
 */
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

/** Extracts the backend's uniform error shape (see `ApiErrorBody`), falling back to a generic message for network-level failures. */
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
