import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import * as authApi from "./api";
import { tokenStorage } from "../../shared/api/tokenStorage";
import type { UserSummary } from "../../shared/types";

interface AuthContextValue {
  user: UserSummary | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: authApi.RegisterInput) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!tokenStorage.getAccessToken()) {
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

export function useCurrentUser(): UserSummary {
  const { user } = useAuth();
  if (!user) {
    throw new Error("useCurrentUser used outside a ProtectedRoute");
  }
  return user;
}
