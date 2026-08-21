# Fortschritt

## Epic A — Identität, Konto & Abrechnung

Branch: `epicA`

- **A1** — Registrierung (E-Mail/Passwort) & Login, SSO via Google/GitHub (PKCE-Code-Exchange), self-issued JWT (RS256) + opake, rotierende Refresh-Tokens.
- **A2** — Backend-seitig fertig: derselbe `POST /api/v1/auth/token`-Endpunkt bedient auch die Chrome-Extension (PKCE). Kein Extension-Client in diesem Repo.
- **A3** — BYOK: AI-Credential-Speicherung mit Envelope-Encryption (AES-256-GCM, austauschbarer KEK-Port) + Live-Verify pro Provider (OpenAI, Anthropic, Google, Azure, OpenRouter, Ollama).
- **A4** — Nutzungs-Dashboard: `GET /api/v1/ai/usage` (Top-5-Kostentreiber).
- **A5** — Konto-Löschung (DSGVO): `DELETE /api/v1/me`, Hard-Delete-Kaskade, Löschnachweis (`account_deletions`).
- **A6** — Free-Tier-Quota: Datenmodell + Guard (`QuotaService.assertWithinFreeQuota`) implementiert, noch nicht an einen LLM-Call-Pfad verdrahtet (kein `LlmGateway`, kommt erst mit M1).

Bewusst nicht umgesetzt: `/api/v1/ai/routing` (ADR-010) — gehört zu M1, keine Epic-A-Story.

### Bekannte Lücken
- Keine Docker-Umgebung in der Entwicklungssession verfügbar → Flyway-Migrationen, `@SpringBootTest`-Kontext und Testcontainers-Tests bislang ungeprüft. `./gradlew test` mit Docker lokal/CI nachholen.
- `users.email` als `TEXT` + funktionalem Unique-Index (`lower(email)`) statt `CITEXT` aus PLAN.md — Abweichung wegen bekannter Hibernate-`ddl-auto=validate`-Inkompatibilität mit `citext`, ungetestet gegen echte Postgres-Instanz.
