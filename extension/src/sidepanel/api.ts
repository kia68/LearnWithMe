import { createApiClient } from "@learnwithme/api-client";

// F4: einziges host_permissions-Ziel im Manifest — keine <all_urls>.
const API_BASE_URL = "http://localhost:8080";
const REFRESH_TOKEN_KEY = "learnwithme.extension.refreshToken";

let accessToken: string | null = null;

export function getStoredRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

function setRefreshToken(token: string | null) {
  if (token) localStorage.setItem(REFRESH_TOKEN_KEY, token);
  else localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export const api = createApiClient({
  baseUrl: API_BASE_URL,
  getAccessToken: () => accessToken,
});

export async function login(email: string, password: string): Promise<boolean> {
  const { data, error } = await api.POST("/api/v1/auth/login", { body: { email, password } });
  if (error || !data) return false;
  accessToken = data.accessToken;
  setRefreshToken(data.refreshToken);
  return true;
}

export async function tryRestoreSession(): Promise<boolean> {
  const refreshToken = getStoredRefreshToken();
  if (!refreshToken) return false;
  const { data, error } = await api.POST("/api/v1/auth/refresh", { body: { refreshToken } });
  if (error || !data) {
    setRefreshToken(null);
    return false;
  }
  accessToken = data.accessToken;
  setRefreshToken(data.refreshToken);
  return true;
}

export function logout() {
  accessToken = null;
  setRefreshToken(null);
}
