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

### Bekannte Lücken (Epic A)

- Keine Docker-Umgebung in der Entwicklungssession verfügbar → Flyway-Migrationen, `@SpringBootTest`-Kontext und Testcontainers-Tests bislang ungeprüft. `./gradlew test` mit Docker lokal/CI nachholen.
- `users.email` als `TEXT` + funktionalem Unique-Index (`lower(email)`) statt `CITEXT` aus PLAN.md — Abweichung wegen bekannter Hibernate-`ddl-auto=validate`-Inkompatibilität mit `citext`, ungetestet gegen echte Postgres-Instanz.

## Epic B — Dokumenten-Import & Ingestion

Branch: `epicA` (fortgesetzt). Neue Module `content`, `knowledge`, `platform`.

- **B1** — PDF-Upload (bis 100 MB, Seitenlimit konfigurierbar): `POST /api/v1/sources` (multipart) → 202 + Job-ID; Status `UPLOADED→EXTRACTING→CHUNKING→INDEXING→READY|PARTIAL|FAILED`; Fortschritt per SSE (`GET /sources/{id}/events`).
- **B2** — URL-Import (`POST /sources { url }`): serverseitige Readability4J-Extraktion; Paywall/JS-only wird über die extrahierte Textmenge erkannt und als `PARTIAL` markiert statt Müll zu speichern.
- **B3** — Extension-Snippet-Import (`POST /sources { html, url, title }`): gleiche Readability-Pipeline wie B2, angewendet auf das vom Client extrahierte DOM-Fragment. Backend-seitig fertig, kein Extension-Client in diesem Repo (wie A2).
- **B4** — DOCX/Markdown/Plaintext/EPUB über dieselbe Tika-Pipeline wie PDF; Markdown-Überschriften zusätzlich per ATX-Regex erkannt (Tika liefert dafür keine HTML-Heading-Tags).
- **B5** — OCR-Bedarfserkennung über Textdichte (Zeichen/Seite unter Schwellwert → `needsOcr=true`); `POST /sources/{id}/ocr` reiht einen opt-in OCR-Job ein (Tesseract via Tika). **Ungetestet** — kein Tesseract in dieser Umgebung installierbar/verifizierbar.
- **B6** — Dokumentstruktur: `h1`-`h6` (bzw. Markdown-ATX-Headings) werden zu einem Section-Baum; `GET /sources/{id}/sections`, `PATCH .../sections/{sid} { excluded }` zum Ausschließen von Bereichen.
- **B7** — Dedup per SHA-256 über die Rohdatei (`UNIQUE(workspace_id, content_hash)`); identischer Upload liefert die bestehende Source zurück statt neu zu extrahieren.
- **B8** — Kernkonzepte: `GET /sources/{id}/concepts`. Extraktion ist **frequenzbasiert, nicht LLM-gestützt** (wiederkehrende, nicht-triviale Wörter + Belegstellen) — bewusste Vereinfachung, da das `LlmGateway` (ADR-004) noch nicht existiert (kommt mit M1). Läuft asynchron über das Modulith-Event `SourceIndexed`, nachdem eine Source `READY` ist.

### Architektur-Notizen

- Neues Modul `platform`: DB-gestützte Job-Queue (`SELECT...FOR UPDATE SKIP LOCKED`, ADR-012) + S3-kompatibler `StorageService` (AWS SDK v2, MinIO lokal/Hetzner-S3 prod). `learnwithme.jobs.enabled` steuert den Worker-Poller (Default `true`, im `api`-Profil `false`).
- `ModularityTest` (`@Tag("architecture")`, läuft jetzt über den bislang ungenutzten `architectureTest`-Gradle-Task) erzwingt `ApplicationModules.verify()`. Dabei aufgedeckt und behoben: `shared.web.ApiException` (+Subklassen) lagen in einem Unterpaket und waren damit für andere Module nach Spring-Modulith-Konvention nicht "öffentlich" — verschoben nach `shared` (Wurzelpaket). Betrifft auch bereits bestehenden Epic-A-Code (`identity`, `ai`), der diese Exceptions nutzt.
- `content.SourceIndexed` liegt im Wurzelpaket, nicht in einem `events`-Unterpaket wie in PLAN.md §6.3 skizziert — ein `events`-Unterpaket bräuchte eine `@NamedInterface`-Markierung, die in einem reinen Kotlin-Sourceset (kein `package-info.java`) nicht risikofrei verifizierbar war.
- `chunks.embedding`/`concepts.embedding` (`vector(1536)`) existieren in der DB, sind aber bewusst nicht in den JPA-Entities gemappt — befüllt wird erst vom `LlmGateway` (M1).
- `jobs.payload` ist `TEXT` (Jackson-JSON) statt `JSONB` — gleiche Abwägung wie bei `CITEXT` in Epic A: Hibernates JSONB-Schreibpfad ungetestet ohne laufende Postgres-Instanz.
- `GET /sources` paginiert per `page`/`size` statt des in PLAN.md skizzierten `?cursor=` — pragmatische Vereinfachung, keine funktionale Lücke.

### Bekannte Lücken (Epic B)

- Wie Epic A: kein Docker hier → Flyway-Migrationen (V3/V4), `@SpringBootTest`, Testcontainers-Tests sowie der `S3StorageService` gegen echtes MinIO ungeprüft.
- OCR-Pfad (B5) ungetestet ohne Tesseract-Installation.
- SSE-Fortschritt (B1) ist In-Memory pro Prozess — funktioniert im `local`-Profil (API+Worker ein Prozess), aber nicht Multi-Instanz-sicher (bräuchte Postgres `LISTEN`/`NOTIFY` oder einen Message-Bus). Bewusst nicht Teil von Epic B.
- A5-Kontolöschung räumt weiterhin keine S3-Objekte auf (bereits in Epic A als offen markiert) — `identity` darf laut Modulgrenzen nicht von `content` abhängen, eine saubere Lösung (z.B. Event-basiert mit korrekter Transaktionsreihenfolge) ist nicht Teil von Epic B.
- Konzeptextraktion (B8) ist eine Häufigkeits-Heuristik, kein semantisches Verständnis — Ablösung durch LLM-basierte Extraktion vorgesehen, sobald `LlmGateway` existiert.
