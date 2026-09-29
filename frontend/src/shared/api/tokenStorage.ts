// localStorage, not an httpOnly cookie - a deliberate simplification for
// this intentionally-simple frontend (see README's frontend section for
// the trade-off this accepts: a successful XSS on this app could read
// these tokens, which a cookie the client-side JS never sees would
// prevent). Centralized here so every other module reads/writes tokens
// through one place instead of touching localStorage directly.

const ACCESS_TOKEN_KEY = "flowdesk.accessToken";
const REFRESH_TOKEN_KEY = "flowdesk.refreshToken";

export const tokenStorage = {
  getAccessToken: (): string | null => localStorage.getItem(ACCESS_TOKEN_KEY),
  getRefreshToken: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),
  setTokens: (accessToken: string, refreshToken: string): void => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clear: (): void => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};
