import type { components } from "@learnwithme/api-client";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, setAccessToken, setUnauthorizedHandler } from "../api/client";

type MeResponse = components["schemas"]["MeResponse"];

const REFRESH_TOKEN_KEY = "learnwithme.refreshToken";

interface AuthContextValue {
  user: MeResponse | null;
  /** true, solange der Refresh-Token beim Start geprüft wird — verhindert einen Login-Flackern. */
  isInitializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName?: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [isInitializing, setInitializing] = useState(true);

  const applySession = useCallback(async (accessTokenValue: string, refreshTokenValue: string) => {
    setAccessToken(accessTokenValue);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshTokenValue);
    const { data } = await api.GET("/api/v1/me");
    setUser(data ?? null);
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);

  useEffect(() => {
    const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!storedRefreshToken) {
      setInitializing(false);
      return;
    }
    api.POST("/api/v1/auth/refresh", { body: { refreshToken: storedRefreshToken } })
      .then(async ({ data }) => {
        if (data) await applySession(data.accessToken, data.refreshToken);
        else clearSession();
      })
      .catch(clearSession)
      .finally(() => setInitializing(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const { data, error } = await api.POST("/api/v1/auth/login", { body: { email, password } });
    if (error || !data) throw new Error("login failed");
    await applySession(data.accessToken, data.refreshToken);
  }, [applySession]);

  const register = useCallback(async (email: string, password: string, displayName?: string) => {
    const { data, error } = await api.POST("/api/v1/auth/register", { body: { email, password, displayName } });
    if (error || !data) throw new Error("registration failed");
    await applySession(data.accessToken, data.refreshToken);
  }, [applySession]);

  const logout = useCallback(async () => {
    const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (storedRefreshToken) {
      await api.POST("/api/v1/auth/logout", { body: { refreshToken: storedRefreshToken } }).catch(() => undefined);
    }
    clearSession();
  }, [clearSession]);

  const value = useMemo(
    () => ({ user, isInitializing, login, register, logout }),
    [user, isInitializing, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
