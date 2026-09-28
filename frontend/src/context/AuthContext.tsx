import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import * as authApi from "../api/auth";
import { tokenStorage } from "../api/tokenStorage";
import type { UserSummary } from "../types";

interface AuthContextValue {
  user: UserSummary | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: {
    organizationName: string;
    firstName: string;
    lastName: string;
    email: string;
    password: string;
  }) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null);
  // Starts true: on a fresh page load with a token already in
  // localStorage, we don't yet know if it's still valid until /me
  // resolves - ProtectedRoute waits for this before deciding to redirect
  // to /login, so a valid session doesn't flash a login screen first.
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = tokenStorage.getAccessToken();
    if (!token) {
      setLoading(false);
      return;
    }
    authApi
      .getCurrentUser()
      .then(setUser)
      .catch(() => tokenStorage.clear())
      .finally(() => setLoading(false));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      async login(email, password) {
        const response = await authApi.login({ email, password });
        tokenStorage.setTokens(response.accessToken, response.refreshToken);
        setUser(response.user);
      },
      async register(input) {
        const response = await authApi.register(input);
        tokenStorage.setTokens(response.accessToken, response.refreshToken);
        setUser(response.user);
      },
      async logout() {
        const refreshToken = tokenStorage.getRefreshToken();
        tokenStorage.clear();
        setUser(null);
        if (refreshToken) {
          // Best-effort - the user is logged out client-side regardless of
          // whether this call succeeds, since the tokens are already
          // discarded either way.
          await authApi.logout(refreshToken).catch(() => undefined);
        }
      },
    }),
    [user, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
