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

## Epic D — Adaptives Frage-Antwort-System

Branch: `epicD` (von `main`, das zu diesem Zeitpunkt Epic A+B+C enthielt). Zwei neue Module
`assessment` (Sessions/Attempts, der kritische Antwort-Pfad) und `adaptivity` (Elo + FSRS, reine
Mathematik ohne LLM- oder Item-Abhängigkeit, §6.3).

- **D1** — `POST /api/v1/sessions { scopeKind, scopeId?, goalKind, goalValue }` → Session +
  erste Frage in einem Roundtrip, kein LLM im Pfad.
- **D2** — Item-Auswahl (§11.3): Kandidatenpool je Scope → Ausschluss zuletzt gesehener Items
  (session-lokales Fenster) → Score `-|P(θ,d) - Zielwert| + Explore-Bonus` → Softmax-Ziehung aus
  Top-5 (τ konfigurierbar). Zielband 0.70–0.85 über `learnwithme.adaptivity.target-success-probability`.
- **D3** — Elo-Update (ADR-005, `adaptivity.internal.engine.EloEngine`): θ/Item-Schwierigkeit nach
  jeder Antwort, unsicherheitsabhängiger K-Faktor, θ/d geklemmt auf `[-4, +4]`. Item-Schwierigkeit
  wird über `AuthoringApi.updateCalibration` zurückgeschrieben (neuer Port, siehe unten).
- **D4** — `POST /sessions/{id}/attempts` liefert Outcome, Score, `feedback.explanation`,
  `feedback.chosenOptionRationale` (typspezifisch, siehe `ResponseGrader`), `feedback.evidence`
  (ganzer zitierter Chunk + Seite — Items zitieren immer einen kompletten Chunk, kein Sub-Zitat,
  siehe Epic-C-`GenerationPipeline`) sowie `learnerUpdate` und das nächste Item — alles synchron,
  kein LLM-Call (N1).
- **D5** — FSRS-Scheduling (ADR-006, `adaptivity.internal.engine.FsrsEngine`): Difficulty/Stability/
  Retrievability, Grade aus dem Score abgeleitet (§11.4-Tabelle). Fälligkeiten über
  `GET /api/v1/progress/due`.
- **D6** — `POST /sessions/{id}/skip { itemId, reason }`: kein Elo-/FSRS-Update (θ unverändert),
  aber `items.skip_count` (neue Spalte, analog `report_count`) als Item-Qualitätssignal.
- **D7** — `GET /api/v1/progress/overview|concepts|due`: Beherrschungsgrad (`mastery`) ist bewusst
  `EloEngine.successProbability(θ, 0)` — die Erfolgswahrscheinlichkeit gegen ein Item
  durchschnittlicher Schwierigkeit, erklärbar (P4) statt einer separaten Formel.
- **D8** — Kein eigener Code: `GET /sessions/{id}/next` ist bereits ein reiner, seiteneffektfreier
  Peek (Prefetch), und Attempts sind ohnehin append-only — das erfüllt die Backend-Voraussetzung
  für Offline-Sync strukturell. Tatsächliches Offline-Caching ist Client-Sache; kein
  Extension-/PWA-Client in diesem Repo (wie A2/B3). Prio `C`, keine weitere Arbeit investiert.
- **D9** — Typ-Rotation: `learnwithme.adaptivity.selection.max-same-type-in-a-row` (Default 2)
  verhindert in `ItemSelectionService`, dass derselbe Fragetyp zu oft in Folge kommt.

### Architektur-Notizen (Epic D)

- **Neuer `authoring.AuthoringApi`-Port** (in Epic C als offene Lücke vermerkt: „nachziehen, sobald
  Epic D das braucht"). Liefert Payload bewusst als roher JSON-String (`payloadJson`) statt als
  `ItemPayload` — dieser Typ ist `internal` (ADR-007), die Modulgrenze erlaubt nur den `type`-String
  + JSON über den Port. `assessment.internal.grading.ResponseGrader` interpretiert den JSON-Payload
  daher unabhängig von `authoring.internal.domain.ItemPayload` (kein Typ überquert die Grenze) —
  gleicher Trick wie ADR-007 selbst, nur einmal zusätzlich angewendet.
- **`adaptivity` kennt keine Items** (§6.3: „adaptivity kennt kein LLM" — hier zusätzlich: auch keine
  `authoring`-Abhängigkeit). Item-Schwierigkeit geht als Parameter in `AdaptivityApi.recordAttempt`
  hinein und als Wert wieder heraus; `assessment` schreibt sie über `AuthoringApi.updateCalibration`
  zurück. Dadurch bleibt die Adaptionsmathematik isoliert testbar (P4) — siehe `EloEngineTest`,
  `FsrsEngineTest`.
- Die Item-Auswahl-Policy-Parameter (`target-success-probability`, `selection.*`) und die reinen
  Elo/FSRS-Modellparameter (`elo.*`, `fsrs.*`) teilen sich die YAML-Wurzel `learnwithme.adaptivity.*`,
  werden aber in zwei verschiedenen Modulen gebunden (`assessment.internal.config.SelectionProperties`
  bzw. `adaptivity.internal.config.AdaptivityProperties`) — Auswahl-Policy ist Sache von `assessment`
  (kennt Items), nicht von `adaptivity` (kennt keine Items).
- **FSRS-Gewichte sind Näherungswerte.** Die Formeln (DSR-Modell, Retrievability-/Intervall-Formel)
  sind nach der offenen FSRS-Spezifikation sauber nachimplementiert; der konkrete 15-Parameter-
  Gewichtsvektor (`FsrsEngine.W`) ist unverifiziert gegen die Referenz-Testvektoren des
  Open-Source-Projekts — genau die in Epic C offen gelassene Frage (§20 O-3). Vor Produktivbetrieb
  verifizieren oder durch eine geprüfte Bibliothek ersetzen.
- **`learner_concept_state` bekommt eine `workspace_id`-Spalte**, die PLAN.md §8.1 nicht vorsieht —
  Abweichung für die zweite Mandanten-Verteidigungslinie (N9), konsistent mit jeder anderen
  mandantenfähigen Tabelle in dieser Codebase.
- `sessions.summary`/`attempts.response` sind `TEXT` statt `JSONB` — dieselbe Abwägung wie
  `items.payload` (Epic C).
- `attempts.item_type` ist denormalisiert aus `items.type`, um für die Typ-Rotation (D9) nicht pro
  Attempt einen zusätzlichen `AuthoringApi`-Roundtrip zu brauchen.
- `content.ChunkView` bekommt ein neues `pageFrom`-Feld (vorher nicht exponiert) — für die
  Seitenangabe im Beleg (D4, §9.3 `evidence.page`).

### Bewusste Vereinfachungen gegenüber PLAN.md §11.3 (Item-Auswahl)

- **Kein Prerequisite-Graph-Filter.** `concepts.concept_relations` (PLAN §8.1) wird von keinem
  Modul befüllt — Epic B extrahiert nur Frequenz-Konzepte, keine Relationen. Ein Filter ohne
  Datenquelle wäre toter Code; nachziehen, sobald `knowledge` Relationen liefert.
- **`MIXED`-Scope vereinfacht auf „fällige Konzepte".** Die volle Drei-Wege-Gewichtung (fällig 0.5 /
  Sessionziel 0.3 / schwächste Konzepte 0.2) bräuchte eine workspace-weite Konzeptliste ohne
  Source-Filter, die `KnowledgeApi` nicht anbietet (nur `listConcepts(sourceId)`).
  `DUE_REVIEW` und `MIXED` verhalten sich aktuell identisch.
- **Kein Paraphrase-Vorzug bei zuletzt falscher Antwort (E6).** Epic C generiert keine
  Paraphrase-Varianten (`parent_item_id` bleibt ungenutzt) — nicht Teil von Epic D.
- **Kein Antwortzeit-Signal** (§11.2: „< 20 % der Median-Zeit → r = 0.9" bzw. §11.4s „EASY *und
  schnell*"). Bräuchte einen laufenden Median pro Item, den keine Story bisher verlangt — Score
  kommt ausschließlich aus dem deterministischen Grading.
- **`p_correct` (Item, empirisch) wird von Epic D nicht gepflegt** — dient laut Datenmodell-
  Kommentar „nur Reports", keine Story braucht es. `AuthoringApi.updateCalibration` unterstützt es
  (Parameter vorhanden), `AttemptService` übergibt aktuell `null`.
- **`GET /progress/concepts` zeigt nur den aktuellen Stand, keinen Verlauf über Zeit** (Teil der
  D7-AC). Bräuchte eine History-Tabelle/Aggregation über `attempts`, die hier nicht gebaut wurde.
- Modulgrenzen weichen von der PLAN.md-§6.3-Tabelle ab: `assessment` hängt zusätzlich von
  `knowledge`(API) (Konzeptliste für Scope `SOURCE`/`CONCEPT`-Progress) und `content`(API)
  (Chunk-Zitat für Belege) ab — von `ApplicationModules.verify()` erlaubt (keine `internal`-Zugriffe,
  keine Zyklen), nur von der informellen Tabelle in PLAN.md nicht vorgezeichnet.

### Bekannte Lücken (Epic D)

- Wie immer: kein Docker hier → Migration V6, `@SpringBootTest`, Testcontainers-Tests ungeprüft.
  Getestet ist ausschließlich reine Domänenlogik ohne Spring-Kontext (`EloEngineTest`,
  `FsrsEngineTest`, `ResponseGraderTest`), gleiches Muster wie Epic C.
- N1 (p95 < 400 ms) und N4 (200 gleichzeitige Sessions) sind unverifiziert — kein Lasttest ohne
  laufende Postgres-Instanz möglich.
- `SHORT_ANSWER`/`NUMERIC`/`CATEGORIZATION` werden nicht gegradet (`ResponseGrader.grade` wirft
  bei unbekanntem Typ) — konsistent mit Epic C, die diese Typen (Prio S/C) nicht generiert.
- Softmax-Ziehung (§11.3 Schritt 4) nutzt `kotlin.random.Random.Default` (nicht injizierbar) —
  für deterministische Tests der Selection-Policy müsste das später ein austauschbarer Port werden;
  aktuell nur die reine Scoring-/Grading-Mathematik ist getestet, nicht die Zufallsauswahl selbst.

## Infrastruktur-Fixes nach Epic D (CI-Kaskade)

Beim ersten tatsächlich laufenden CI-Lauf (`gradlew` bekam erstmals das Ausführungsbit, siehe
Commit-Historie) sind sechs vorbestehende, bis dahin unsichtbare Lücken aufgefallen — keine davon
in Epic-D-Fachcode, alle nur unsichtbar, weil `@SpringBootTest` in keiner Entwicklungssession
(kein lokales Docker) je tatsächlich gebootet ist:

1. `gradlew` war als `100644` statt `100755` getrackt (Windows-Checkout verschleiert das lokal).
2. `spring-boot-docker-compose` ist bewusst `developmentOnly` und damit nicht auf dem
   Testklassenpfad → `@SpringBootTest` brauchte eigene Infrastruktur: Testcontainers-Postgres
   (`pgvector/pgvector:pg17`, wegen `CREATE EXTENSION vector`) und -MinIO (`S3StorageService`
   verbindet sich per `@PostConstruct`), beide in `LearnWithMeApplicationTests`.
3. Spring Boot 4 hat `FlywayAutoConfiguration`/`FlywayMigrationStrategy` in ein eigenes Artefakt
   `spring-boot-flyway` ausgelagert — ohne es lief Flyway nie (kein Fehler, keine Log-Zeile),
   `ddl-auto=validate` scheiterte mit „missing table" gegen ein leeres Schema.
4. `spring-modulith-starter-jpa` braucht die `event_publication`-Tabelle, legt sie aber nicht
   selbst an (V7-Migration, DDL aus der Modulith-Referenzdokumentation übernommen — bewusst nicht
   `schema-initialization.enabled=true`, gleiche Begründung wie bei der `pgvector`-Extension: Schema
   nur aus Flyway, §8.3).
5. `@Component`-registrierte `@ConfigurationProperties`-Klassen mit verschachtelten
   Konstruktorparametern (`AiRoutingProperties` aus Epic C, `AdaptivityProperties`/
   `SelectionProperties` aus Epic D) lösten Spring Boots normale `@Autowired`-Konstruktor-Auflösung
   aus statt Properties-Binding → `NoSuchBeanDefinitionException` für die verschachtelten Typen.
   Fix: `@ConfigurationPropertiesScan` auf `LearnWithMeApplication`, `@Component` entfernt.
6. Sowohl der `openai`- als auch der `ollama`-Spring-AI-Starter sind auf dem Klassenpfad (ADR-004)
   und konfigurieren beide unbedingt ein `EmbeddingModel`-Bean →
   `PgVectorStoreAutoConfiguration` fand zwei Kandidaten. Fix: `spring.ai.model.embedding=openai`
   (per Jar-Introspektion verifiziert, gleiche Methode wie schon in Epic C für Spring-AI-API-Fragen).

Alle sechs Fixes sind auf `main` (nicht auf einem Epic-Branch) gelandet, einzeln per CI verifiziert,
bevor der jeweils nächste angegangen wurde.

## Epic E — Echtzeit-Fehlerkorrektur & -analyse

Branch: `epicE` (von `main`, das zu diesem Zeitpunkt Epic A-D und die obige CI-Kaskade enthielt).
Neues Modul `analytics` (Fehlerklassifikation, Misconception-Aggregation, Wochenreport) — bewusst
OHNE `ai`-Abhängigkeit (anders als in PLAN.md §6.3 skizziert): E1 ist per eigenem Architekturprinzip
LLM-frei zur Laufzeit (§11.5), es gibt daher nichts, wofür `analytics` einen `LlmGateway`-Zugriff
bräuchte. `ai-routing.error-analysis` (seit Epic D in application.yml vorprovisioniert) bleibt
unkonsumiert — reserviert für eine mögliche spätere asynchrone LLM-Verfeinerung.

- **E1** — `analytics.internal.classification.ErrorClassifier` (reine Funktion, kein I/O):
  Reihenfolge AMBIGUOUS_ITEM (θ_vorher > Item-Schwierigkeit + Margin) → CARELESS (erwartete
  Erfolgswahrscheinlichkeit hoch + sehr kurze Antwortzeit) → Distraktor-Tag
  (`misconceptionCategory`, neues Feld auf `authoring.Option`, von der LLM-Generierung befüllt,
  C8-Analogon) → Typ-Fallback (ORDERING/MATCHING/CLOZE → PROCEDURAL, sonst FACTUAL_GAP). Ergebnis
  landet synchron in `POST /sessions/{id}/attempts`s neuem `errorAnalysis`-Feld (§9.3).
- **E2** — Nachfrage zur selben Quellstelle: `AttemptService` ruft nach einer Fehlklassifikation
  `ItemSelectionService.selectNext(..., preferredConceptId = item.conceptId)` — die Selection-Policy
  bleibt dann exklusiv im selben Konzept, statt dem üblichen Scope-Pool zu folgen.
- **E3** — `misconceptions`-Tabelle: Upsert (user, concept, category) bei jedem klassifizierten
  Fehler (außer CARELESS/AMBIGUOUS_ITEM — beide sind laut §11.5 explizit keine Wissenslücken).
  `occurrences >= misconception-threshold` (Default 3) IST die Flag — kein separates Boolean-Feld.
  `GET /api/v1/progress/misconceptions`.
- **E4** — **nicht gebaut.** Freitext-gegen-Rubric-Bewertung braucht `SHORT_ANSWER`-Items, die
  Epic C bewusst nicht generiert (Prio S, kein Story-Bedarf damals). Ohne Fragetyp keine Antworten
  zum Bewerten — ein neuer Fragetyp ist Authoring-/Epic-C-Scope, nicht Epic E. Echter Blocker,
  keine Verzögerung.
- **E5** — `GET /api/v1/reports/weekly`: Top-3 schwächste Konzepte nach `mastery`
  (`AdaptivityApi.listAllProgress`) + Konzeptnamen (`KnowledgeApi.getConcept`), `recommendedFocus`
  = schwächstes Konzept. **Nur In-App** (keine SMTP-Infrastruktur in dieser Codebase) und **ohne
  Trend** (bräuchte eine History-Tabelle über Mastery-Werte, die nirgendwo geführt wird — derselbe
  bereits in Epic D dokumentierte „kein Verlauf über Zeit"-Gap).
- **E6** — Paraphrase-Generierung: neuer `GenerationPipeline.generateParaphrase(workspaceId,
  originalItemId)` — gleicher Chunk/Konzept/Typ wie das Original, neuer Prompt
  (`PromptBuilder.userPromptForParaphrase`), **ohne** `DuplicateGate` (der würde eine gelungene
  Paraphrase fälschlich als Duplikat verwerfen — das ist ja der Zweck), `parentItemId` verlinkt sie.
  Ausgelöst asynchron über die bestehende Job-Queue (`JobType.GENERATE_PARAPHRASE`, ADR-012,
  idempotent über den `jobKey` — ein Versuch pro Original-Item) direkt aus `analytics` heraus, wenn
  eine "echte" Wissenslücke (nicht CARELESS/AMBIGUOUS_ITEM) klassifiziert wurde. Landet als
  gewöhnliches `DRAFT`-Item im normalen C7-Review-Workflow. `ItemSelectionService` bevorzugt beim
  Wiedersehen (`preferParaphraseOfItemId`) direkt ein Item mit `parentItemId == das zuletzt falsch
  beantwortete Item`, falls eines bereits veröffentlicht ist.

### Architektur-Notizen (Epic E)

- **Bewusst synchron statt Modulith-Event.** PLAN.md §6.5 skizziert `AttemptRecorded` als
  asynchrones Event für `analytics`. Da die Klassifikation selbst lastenfrei ist (kein LLM, kein
  Netzwerk-Call — §11.5s eigene Kernaussage), gibt es keinen Latenzgrund für Async;
  `AnalyticsApi.analyzeError` ist ein normaler synchroner Port-Aufruf aus `assessment` heraus,
  genau wie `AdaptivityApi`/`AuthoringApi` in Epic D. Nur die eigentliche Paraphrase-*Generierung*
  (LLM) läuft asynchron — über die bereits bestehende Job-Queue, nicht über ein neues
  Event-Listener-Konstrukt.
- **`analytics` hängt zusätzlich von `adaptivity`(API) und `knowledge`(API) ab** (E5-Wochenreport)
  — Abweichung von der PLAN.md-§6.3-Tabelle (`analytics ──▶ shared, ai`), von
  `ApplicationModules.verify()` erlaubt, gleiche Art Abweichung wie schon `assessment` in Epic D.
- **`Option.misconceptionCategory`** ist eine rückwärtskompatible, nullable Ergänzung
  (`authoring.internal.domain.ItemPayload`) — bestehende Items ohne dieses Feld deserialisieren
  weiterhin (Kotlin-Default `null`). `PromptBuilder` fordert es jetzt explizit für MC_SINGLE/
  MC_MULTI-Distraktoren an; **bereits generierte Items vor diesem Commit haben es nicht** und
  fallen bei der Fehlerklassifikation auf den Typ-Fallback zurück (FACTUAL_GAP/PROCEDURAL) — kein
  Nachtrag für Altbestand vorgesehen.
- `error_events.attempt_id`/`item_id`/`concept_id` sind lose ID-Referenzen ohne JPA-`@ManyToOne`
  über Modulgrenzen — wie überall sonst in dieser Codebase (z. B. `items.concept_id`).
- `misconceptions` nutzt `category` (feste Taxonomie) statt PLAN.md §8.1s freiem `label` —
  es gibt keine über §11.5 hinausgehenden Misconception-Bezeichnungen zu unterscheiden.

### Bekannte Lücken (Epic E)

- **E4 nicht gebaut** (siehe oben — echter Blocker, kein Story-Bedarf ohne `SHORT_ANSWER`-Items).
- **AMBIGUOUS_ITEM hat keine Review-Queue-Anbindung.** §11.5: „Item flaggen, nicht den Nutzer →
  Review-Queue". Das ErrorEvent wird korrekt klassifiziert und persistiert, aber es gibt keinen
  Statusübergang (PUBLISHED → zurück in Review) und kein aggregiertes „N starke Lernende sind
  gescheitert"-Signal auf Item-Ebene — jedes Ereignis steht nur einzeln in `error_events`. Eine
  Auswertung dieser Tabelle könnte das später nachliefern, ohne Schemaänderung.
- **Kein Antwortzeit-Median pro Item** (wie schon in Epic D dokumentiert) — CARELESS nutzt einen
  festen absoluten Schwellwert (`careless-max-elapsed-ms`) statt eines Item-relativen Werts.
- **E5 ohne Trend und ohne E-Mail** (siehe oben).
- Wie immer: kein Docker hier → Migration V8, `@SpringBootTest`-Pfade (Flyway-Schema-Validierung
  gegen `error_events`/`misconceptions`, tatsächlicher Paraphrase-Job-Lauf) ungetestet. Getestet ist
  ausschließlich reine Domänenlogik (`ErrorClassifierTest`, `ResponseGraderTest`-Ergänzungen).
