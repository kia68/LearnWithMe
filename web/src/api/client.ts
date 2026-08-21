import { createApiClient } from "@learnwithme/api-client";

// Leer = gleicher Origin wie die Web-App selbst. Im Dev-Server reicht Vites `server.proxy`
// (vite.config.ts) `/api` an das Backend weiter — der Browser spricht nie direkt mit Port 8080
// (vermeidet CORS und funktioniert auch, wenn nur ein Port nach außen erreichbar ist).
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

let accessToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

/** Ein Client-Singleton für die ganze App (ADR-011) — Token/Handler werden von `AuthContext`
 * synchronisiert, nicht über React-Props durchgereicht, da auch Nicht-React-Code (z.B. spätere
 * Service-Worker-Hintergrundaufrufe) denselben Client nutzen können soll. */
// baseUrl ist nur der Host — die generierten `paths`-Schlüssel enthalten bereits `/api/v1/...`
// (so, wie springdoc sie dokumentiert); ein zusätzliches Prefix hier würde es verdoppeln.
export const api = createApiClient({
  baseUrl: API_BASE_URL,
  getAccessToken: () => accessToken,
  onUnauthorized: () => unauthorizedHandler?.(),
});
