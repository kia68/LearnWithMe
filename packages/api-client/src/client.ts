import createClient from "openapi-fetch";
import type { paths } from "./schema";

export interface ApiClientOptions {
  baseUrl: string;
  /** Liest den aktuellen Access-Token je Aufruf — kein Caching hier, der Aufrufer entscheidet, wo/wie der Token liegt (§14). */
  getAccessToken: () => string | null;
  /** 401 auf einer authentifizierten Route — i.d.R. Logout/Redirect zum Login auslösen. */
  onUnauthorized?: () => void;
}

/** ADR-011: ein generierter, typisierter Client für Web-App und Extension. */
export function createApiClient({ baseUrl, getAccessToken, onUnauthorized }: ApiClientOptions) {
  const client = createClient<paths>({ baseUrl });

  client.use({
    onRequest({ request }) {
      const token = getAccessToken();
      if (token) request.headers.set("Authorization", `Bearer ${token}`);
      return request;
    },
    onResponse({ response }) {
      if (response.status === 401) onUnauthorized?.();
      return response;
    },
  });

  return client;
}

export type ApiClient = ReturnType<typeof createApiClient>;
