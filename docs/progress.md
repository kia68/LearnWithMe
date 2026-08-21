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

## Epic C — Fragegenerierung & Qualität

Branch: `epicC` (von `main`, das zu diesem Zeitpunkt bereits Epic A+B enthielt). Neues Modul
`authoring`; `ai` bekommt erstmals ein echtes `LlmGateway` (bisher nur Datenmodell/BYOK-Speicherung).

- **C1** — Generierung je Konzept: `POST /concepts/{id}/items:generate { count, types[] }` → 202,
  asynchroner Job (`GENERATE_ITEMS`, ADR-012). 6 MVP-Fragetypen (§10.1: `MC_SINGLE`, `MC_MULTI`,
  `TRUE_FALSE`, `ORDERING`, `MATCHING`, `CLOZE`) — `SHORT_ANSWER`/`NUMERIC`/`CATEGORIZATION` (Prio S)
  und `HOTSPOT`/`CODE_OUTPUT` (Prio C) bewusst nicht gebaut. Jedes Item zitiert genau einen Chunk
  als Beleg (`source_chunk_id`/`source_char_from`/`source_char_to`).
- **C2** — Groundedness-Gate (ADR-008): Embedding-Kosinus-Ähnlichkeit UND LLM-Judge (nur der
  zitierte Chunk als Kontext) müssen beide zustimmen; sonst `status=REJECTED`,
  `quality.rejectionReason=UNGROUNDED`, kein Nutzerkontakt.
- **C3** — Strukturvalidatoren pro Fragetyp (genau 1 korrekte Option bei MC_SINGLE, ≥2 bei
  MC_MULTI, keine Duplikate/verbotenen Muster wie „alle der genannten", Optionslängen-Varianz
  unter Schwelle, Pflicht-Rationale je Option).
- **C4** — Duplikaterkennung: pgvector-Kosinus-Distanz gegen die Item-Bank **desselben Dokuments**
  (`concepts.source_id`), Schwelle konfigurierbar.
- **C5** — `POST /items/{id}/report { reason, comment? }` — speichert den Report vollständig
  (neue Tabelle `item_reports`) und erhöht `report_count`. Entfernt das Item **nicht** aus einer
  Rotation — es gibt noch keine (Epic D/`assessment` fehlt), siehe Lücken.
- **C6** — „Mehr üben": derselbe Generate-Endpunkt, kein Job-Dedup (jeder Aufruf ein neuer Job,
  bewusst — wiederholte On-Demand-Generierung ist der Zweck der Story).
- **C7** — Review-Queue: `GET /items/review-queue?status=DRAFT`, `POST /items/{id}/publish|reject`,
  `POST /items:bulk-publish|bulk-reject { ids[] }`. Nur `DRAFT → PUBLISHED|REJECTED` erlaubt.
- **C8** — Jede Distraktor-Option trägt eine Pflicht-`rationale` (Prompt-Vorgabe + `MISSING_RATIONALE`-
  Strukturgate).

### Architektur-Notizen (Epic C)

- **`ai.LlmGateway`/`EmbeddingGateway`** (ADR-004) erstmals real implementiert (`ai.internal.llm.SpringAiLlmGateway`/
  `SpringAiEmbeddingGateway`), BYOK-first über `CredentialResolver` (verifiziertes Credential vor
  Plattform-Key), `QuotaService.assertWithinFreeQuota` läuft jetzt vor jedem Plattform-Call (schließt
  die in Epic A offene Lücke). `ModelRouter` bindet `learnwithme.ai-routing.*` (ADR-010) — nur
  `openai` konkret verdrahtet, da die Default-Konfiguration jede Task-Klasse auf `openai` routet;
  Anthropic/Ollama folgen demselben Muster, sind aber ohne konkreten Story-Bedarf nicht gebaut.
- Spring AI **2.0.0-M8** hat gegenüber älteren/GA-Dokumentation eine grundlegend andere OpenAI-
  Integration: kein `OpenAiApi`-HTTP-Client mehr, stattdessen wird der **offizielle `com.openai:openai-java`-SDK**
  (`OpenAIOkHttpClient`) durchgereicht (`OpenAiChatModel.builder().openAiClient(...)`). Über
  Jar-Introspektion (`javap`) verifiziert, nicht aus (potenziell veralteter) Dokumentation geraten —
  ADR-004 warnt selbst vor genau solchen Bruchstellen über Milestone-Versionen.
- Structured Output bewusst **nicht** über Spring AIs `.entity()`/`BeanOutputConverter` (gleicher
  ADR-004-Grund), sondern: Prompt fordert explizit JSON an, Antwort wird mit Jackson in flache,
  typspezifische „Draft"-Datenklassen geparst (`authoring.internal.generation.ItemDrafts`).
- **Neue, sicherheitsrelevante Erkenntnis:** `ObjectMapper` (`com.fasterxml.jackson.databind`, das
  überall in dieser Codebase verwendet wird) kann Kotlin-`data class`es mit `val`-Konstruktorparametern
  ohne registriertes Kotlin-Modul **nicht zuverlässig deserialisieren** — nur Serialisierung und
  `JsonNode`-Baumzugriff (wie in `OAuth2CodeExchangeService`, Epic A) funktionieren ohne. Das im
  Projekt deklarierte `jackson-module-kotlin` ist `tools.jackson.module` (Jackson **3.x**, anderer
  Namespace, inkompatibel mit dem klassischen `ObjectMapper`) — nur von Spring AI intern gebraucht.
  Ergänzt: `com.fasterxml.jackson.module:jackson-module-kotlin` (klassisch, 2.x, BOM-verwaltet) +
  zentrale `shared.JsonMapper` mit registriertem Kotlin-Modul. Mit einem echten Round-Trip-Test
  verifiziert (`PayloadCodecTest`), nicht nur angenommen. Das behebt nebenbei ein latentes,
  bisher unbemerktes Risiko in **allen** bisherigen `@RequestBody`-Kotlin-DTOs aus Epic A/B (Spring
  Boot registriert das jetzt automatisch auf seiner eigenen autokonfigurierten `ObjectMapper`-Bean).
- `items.payload`/`items.quality` sind `TEXT` statt `JSONB` — gleiche Abwägung wie `jobs.payload`
  (Epic B) und `CITEXT` (Epic A).
- `items.embedding` (`vector(1536)`) ist wie `chunks`/`concepts` nicht JPA-gemappt; Duplikaterkennung
  läuft über native pgvector-Queries (`<=>`-Operator) mit einem Textliteral-Helper (`PgVectorFormat`).
- `ItemPayload` ist ein Sealed Interface, aber **ohne** Jacksons `@JsonTypeInfo`-Polymorphie (ADR-007
  skizziert das) — De-/Serialisierung dispatcht manuell über die separate `items.type`-Spalte
  (`PayloadCodec`). Vermeidet eine weitere Jackson-Konfigurationsfläche, bei ansonsten gleicher
  Absicherung (`when` ohne `else`).
- Kein öffentlicher `authoring`-API-Port (`ItemGenerationApi`, wie in PLAN.md §6.3 skizziert) —
  nichts außerhalb des Moduls konsumiert ihn bisher (kein `assessment`-Modul); nachziehen, sobald
  Epic D das braucht.

### Bekannte Lücken (Epic C)

- **Kein Netzwerkzugriff auf echte LLM-Provider in dieser Entwicklungsumgebung** — die gesamte
  `LlmGateway`/`EmbeddingGateway`-Kette ist real und korrekt gegen die Spring-AI-2.0.0-M8-API gebaut
  (per Jar-Introspektion verifiziert) und mit einem echten Kotlin-Serialisierungs-Roundtrip-Test
  abgesichert — ein tatsächlicher End-to-End-Call gegen OpenAI (Prompt → Antwort → Kostenberechnung)
  konnte hier nicht ausgeführt werden. Vor Produktivbetrieb mit einem echten `OPENAI_API_KEY` verifizieren.
- `@ConfigurationProperties`-Konstruktor-Binding (`AiRoutingProperties`) ist ein neues Muster in
  dieser Codebase (bisher überall `@Value` pro Feld) — ungetestet ohne laufenden Spring-Kontext
  (Docker-Lücke).
- C5: Reports werden vollständig gespeichert, aber ohne `assessment`-Modul gibt es keine
  Nutzer-Rotation, aus der ein Item entfernt werden könnte — die AC „aus meiner Rotation genommen"
  ist damit nur zur Hälfte erfüllbar.
- C7: nur die Backend-Endpunkte der Review-Queue, keine UI.
- Kostenschätzung (`SpringAiLlmGateway.estimateCostMicros`) ist eine grobe, hartcodierte
  Preistabelle für `gpt-4o`/`gpt-4o-mini` — keine Abrechnungsgrundlage, dient nur A4/A6.
- Wie immer: kein Docker hier → Migration V5, `@SpringBootTest`, Testcontainers-Tests ungeprüft.
