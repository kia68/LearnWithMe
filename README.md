# LearnWithMe

**Adaptive, KI-gestützte Lernplattform.** Dokumente und Webseiten importieren → interaktive Fragen generieren → Fehler analysieren → Schwierigkeit in Echtzeit anpassen.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17%20+%20pgvector-4169E1)](https://github.com/pgvector/pgvector)
[![Status](https://img.shields.io/badge/Status-M0%20Walking%20Skeleton-orange)]()

---

## Worum es geht

Lernende haben Material — Skripte, Paper, Dokumentation — aber kein Feedback. Sie lesen passiv, überschätzen ihr Verständnis und merken erst in der Prüfung, was fehlt.

Bestehende Werkzeuge lösen jeweils nur ein Teilstück:

| | Import | Auto-Generierung | Adaptivität | Spaced Repetition | Fehleranalyse |
|---|:---:|:---:|:---:|:---:|:---:|
| Anki | – | – | – | ★★★ | – |
| Quizlet | teilweise | ★★ | ★ | ★★ | – |
| ChatGPT / Claude | ★★★ | ★★★ | – | – | ★ flüchtig |
| NotebookLM | ★★★ | ★★ | – | – | – |
| **LearnWithMe** | ★★★ | ★★★ | ★★★ | ★★★ | ★★★ |

Die Lücke, die wir besetzen, ist nicht ein einzelnes Feature, sondern der **geschlossene Regelkreis**: Import **und** Adaptivität **und** Gedächtnismodell. Jeder Baustein existiert einzeln; die Kombination nicht.

### Die drei Hauptfunktionen

1. **Dokumenten-Import** — PDF, DOCX, EPUB, Webseite per URL oder direkt aus dem Browser. Ergebnis ist nicht nur Text, sondern ein **Konzeptgraph mit Belegstellen**.
2. **Adaptives Frage-Antwort-System** — MC (einfach/mehrfach), Wahr/Falsch, Reihenfolge, Zuordnung, Lückentext, Kurzantwort, numerisch, Kategorisierung. Die nächste Frage richtet sich nach deinem Können, nicht nach einer festen Reihenfolge.
3. **Echtzeit-Fehlerkorrektur** — Das System erkennt, *warum* eine Antwort falsch war, und reagiert gezielt: Kontrastfrage bei Begriffsverwechslung, Paraphrase statt Wiederholung, gezielte Wiedervorlage.

---

## Die vier Entscheidungen, die alles andere bestimmen

Wer nur eine Sache über dieses Projekt lesen will, sollte diese vier lesen. Ausführlich in [`docs/PLAN.md`](docs/PLAN.md).

### 1. `Concept` ist die zentrale Achse — nicht `Document`, nicht `Item`

Fähigkeit, Gedächtnis, Fehler und Fortschritt hängen alle am **Konzept**. Dadurch überträgt sich Wissen zwischen Dokumenten (dasselbe Konzept in zwei Skripten ergibt einen Lernstand), und einzelne Fragen bleiben austauschbar, ohne dass der Lernfortschritt verloren geht.

### 2. Kein LLM im synchronen Antwortpfad

Jeder Distraktor bekommt schon **bei der Generierung** eine Begründung und einen `misconceptionTag`. Die „Echtzeit-Fehlerkorrektur" fühlt sich für den Nutzer wie eine Live-Analyse an, ist aber vorberechnet.

Das ist der Grund, warum `POST /attempts` p95 unter 400 ms bleiben kann. LLM-Aufrufe passieren nur, wenn wirklich Neues nötig ist — Freitext-Bewertung, neue Paraphrase — und dann asynchron mit optimistischem UI.

### 3. Elo und FSRS getrennt: Können ≠ Erinnern

| | **Elo** | **FSRS** |
|---|---|---|
| Beantwortet | „Wie schwer darf die nächste Frage sein?" | „Wann muss ich das wiedersehen?" |
| Steuert | *welches Item* | *ob und wann* das Konzept drankommt |

Elo statt IRT, weil jedes neu generierte Item ein Cold-Start-Fall ist — IRT bräuchte Vorkalibrierung an großen Stichproben, die es hier nie geben wird. Das Modul `adaptivity` hat bewusst **keine** Abhängigkeit zum LLM-Modul: reine, testbare, simulierbare Mathematik. ArchUnit erzwingt das.

### 4. Fragegenerierung ist eine Pipeline mit Qualitätstoren, kein Prompt

```
Concept + Belegchunks
   → [1] Generierung (Structured Output)
   → [2] Gate: strukturell    (kein LLM, Millisekunden)
   → [3] Gate: Groundedness   (Judge sieht NUR den zitierten Chunk)
   → [4] Gate: Duplikat       (Vektorsuche)
   → PUBLISHED
```

Ohne diese Tore ist die Fragenqualität das Produktrisiko Nummer eins. Deshalb steht der Eval-Harness mit Gold-Set schon in Meilenstein M2 und nicht später.

---

## Stack

| Schicht | Technologie | Warum |
|---|---|---|
| Sprache | Kotlin 2.3, Java 25 Toolchain | Sealed Interfaces modellieren Fragetypen als geschlossenen Typ — neuer Typ ⇒ Compiler-Fehler an jeder Behandlungsstelle |
| Framework | Spring Boot 4.1 | Modulare Starter, Java 21+ |
| Architektur | Spring Modulith 2.0 | Modularer Monolith mit erzwungenen Grenzen (ADR-001) |
| Datenbank | PostgreSQL 17 + pgvector | Relational, Vektoren, Job-Queue und Outbox in **einer** Datenbank (ADR-002) |
| Migrationen | Flyway | `ddl-auto=validate`, Schema kommt nie von Hibernate |
| KI | Spring AI hinter eigenem Port | Provider austauschbar: Cloud, lokal (Ollama), hybrid, BYOK (ADR-004) |
| Extraktion | Apache Tika, Readability4J | Tika für Dateien, Readability4J für Web — identisch zum Extension-Pfad |
| API | REST + SSE, springdoc/OpenAPI | OpenAPI ist Single Source of Truth für beide Clients (ADR-011) |
| Build | Gradle 9.5 (Kotlin DSL) + Version Catalog | Alle Versionen an einer Stelle |
| Test | JUnit 5, Testcontainers, ArchUnit, MockK, Kotest | Architekturregeln sind Tests, keine Konvention |

---

## Schnellstart

### Voraussetzungen

- JDK 25 (oder Gradle Toolchain lädt es nach)
- Docker (für Postgres und MinIO)
- [GitHub CLI](https://cli.github.com) — nur für das Backlog-Script

### Starten

```bash
git clone <repo-url>
cd learnWithMe

# Postgres + MinIO starten (oder von Spring Boot automatisch beim Start)
docker compose up -d

./gradlew bootRun
```

| | |
|---|---|
| API | http://localhost:8080 |
| OpenAPI / Swagger | http://localhost:8080/api-docs |
| Actuator Health | http://localhost:8080/actuator/health |
| Modulith-Struktur | http://localhost:8080/actuator/modulith |
| MinIO Console | http://localhost:9001 (`learnwithme` / `learnwithme`) |

### Tests

```bash
./gradlew test                 # Unit + Integration (Testcontainers)
./gradlew architectureTest     # Modulgrenzen: Modulith verify + ArchUnit
./gradlew build                # alles
```

### KI-Zugang konfigurieren

Für die lokale Entwicklung reicht eine Umgebungsvariable:

```bash
export OPENAI_API_KEY=sk-...
# oder rein lokal, ohne Cloud:
export OLLAMA_BASE_URL=http://localhost:11434
```

In der laufenden Anwendung hinterlegen Nutzer ihre eigenen Zugänge über `POST /api/v1/ai/credentials` (BYOK). Diese werden per Envelope Encryption gespeichert und **nie** zurückgegeben — siehe [Sicherheit](#sicherheit-und-datenschutz).

> **⚠ Vor dem ersten Build prüfen:** Spring AI 2.x befand sich Mitte 2026 im Übergang von Milestone zu GA. Die Version steht in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) unter `springAi`. Wenn eine GA-Version verfügbar ist: dort eintragen und das Milestone-Repository in `build.gradle.kts` entfernen.

---

## Architektur

### Modulschnitt

Ein Deployment-Artefakt, zehn Module mit erzwungenen Grenzen. `ApplicationModules.verify()` bricht den Build bei Verstößen.

```
de.optadata.odil.learnwithme
├── shared/      Value Objects, IDs, Fehlertypen         (von allen nutzbar)
├── identity/    User, Workspace, Auth, Plan, Quota
├── ai/          ⭐ LLM-Abstraktion, Routing, Credentials, Kosten
├── content/     Source, Ingestion, Extraktion, Chunking
├── knowledge/   Konzeptextraktion, Konzeptgraph, Embeddings, Retrieval
├── authoring/   Item-Generierung, Quality-Gates, Item-Bank
├── assessment/  Session, Item-Auslieferung, Grading, Feedback
├── adaptivity/  Elo-Lernermodell, FSRS-Scheduler, Selection Policy
├── analytics/   Fehlertaxonomie, Misconceptions, Reports
└── platform/    Jobs, Outbox, Storage, Observability
```

Jedes Modul hat `api/` (öffentlich: Ports, DTOs, Events) und `internal/` (alles andere). Modulübergreifend nur über `api/` oder Events.

**Zwei Regeln, die nicht verhandelbar sind:**

```
adaptivity  ⇏  ai      Adaption ist deterministische Mathematik.
                       Ein LLM in der Item-Auswahl kostet Latenz,
                       Reproduzierbarkeit und Auditierbarkeit.

Spring-AI-Typen nur in ai/internal
                       Sonst wandert der nächste Breaking Change
                       des Frameworks durch die halbe Codebasis.
```

Beide sind als ArchUnit-Tests implementiert und brechen den Build.

### Der kritische Pfad

```
POST /sessions/{id}/attempts
   │
   ├─ 1  Grading (deterministisch)                        ~1 ms
   ├─ 2  Attempt persistieren (append-only)               ~3 ms
   ├─ 3  Elo-Update θ_user, d_item                        <1 ms
   ├─ 4  FSRS-Update der MemoryCard                       <1 ms
   ├─ 5  Fehlerklassifikation aus dem misconceptionTag     0 ms  ← kein LLM
   ├─ 6  Nächstes Item wählen                             ~15 ms
   └─ 7  Feedback + nächstes Item in einer Antwort        p95 < 400 ms
```

Ein Roundtrip liefert Bewertung, Erklärung, Beleg, Lernstand-Update **und** die nächste Frage.

### Datenmodell in einem Bild

```
Source ──▶ Document ──▶ Chunk ──┐
                                 ├──▶ Concept ──▶ Item ──▶ Attempt
              ConceptRelation ◀──┘        │                   │
              (prerequisite_of)           │                   ▼
                                          └──▶ LearnerState (θ, MemoryCard)
                                                     │
                                                     ▼
                                             ErrorEvent ──▶ Misconception
```

Vollständiges Schema in [`docs/PLAN.md` §8](docs/PLAN.md).

---

## Sicherheit und Datenschutz

Datenschutz ist hier kein Compliance-Anhängsel, sondern ein Vertriebsargument. Institutionelle Gatekeeper blockieren Werkzeuge, die Skripte an US-Anbieter senden.

| Thema | Umsetzung |
|---|---|
| **BYOK-Credentials** | Envelope Encryption: DEK (AES-256-GCM) pro Credential, KEK im externen KMS. In der DB nur Ciphertext, gewrappter DEK und die letzten vier Zeichen als Hinweis. Kein API-Pfad gibt den Klartext zurück. |
| **Mandantentrennung** | Zwei Verteidigungslinien: Tenant-Filter in der Anwendung **und** PostgreSQL Row Level Security. Ein Test erzwingt Cross-Tenant-Isolation über alle Repository-Methoden. |
| **Prompt Injection** | Dokumentinhalt landet ausschließlich in der User-Rolle, nie im Systemprompt. Ausgabeschema wird erzwungen. Gates prüfen Groundedness, nicht Instruktionsbefolgung. |
| **SSRF** | URL-Import: nur http/https, private IP-Bereiche blockiert, Redirect-Limit, Timeout, Größenlimit. |
| **Uploads** | Größen- und Seitenlimit, XXE deaktiviert, Extraktion mit Zeit- und Speicherbudget. |
| **Lokaler Modus** | Vollbetrieb über Ollama — die Dokumente verlassen das Netz nicht. |
| **Löschung** | Kaskadiert vollständig inklusive S3-Objekten, mit Audit-Log-Nachweis. |

Vollständiges Bedrohungsmodell in [`docs/PLAN.md` §15](docs/PLAN.md).

---

## Projektmanagement

Das gesamte Backlog liegt versioniert im Repo und wird per Script nach GitHub gespiegelt.

```
scripts/github/
├── backlog.json      ← Quelle der Wahrheit: 7 Epics, 56 Stories, 8 Spikes
└── seed_github.py    ← legt alles in GitHub an (idempotent)
```

### Backlog nach GitHub übertragen

```bash
gh auth login
gh auth refresh -s project,read:project,repo    # für das Projekt-Board

cd scripts/github
python seed_github.py --dry-run                 # erst ansehen
python seed_github.py                           # dann anlegen
```

Das Script legt an:

- **26 Labels** — Typ, Priorität (MoSCoW), Epic, Bereich, Risiko
- **7 Milestones** — M0 bis M6 mit Fälligkeitsdaten
- **7 Epic-Issues** mit Ziel, Ergebnis und Risiko
- **64 Sub-Issues** — 56 User Stories und 8 Spikes, jeweils mit User Story, Akzeptanzkriterien, technischen Hinweisen und Definition of Done
- **Native Sub-Issue-Verknüpfungen** — GitHub zeigt den Fortschritt pro Epic automatisch
- **Projects-v2-Board** mit den Feldern Status, Priorität, Epic und Schätzung

**Idempotent.** Ein zweiter Lauf legt nichts doppelt an. Jedes Issue trägt einen unsichtbaren Marker (`<!-- lwm:key=C8 -->`) im Body — die Zuordnung funktioniert auch dann noch, wenn du den Titel in GitHub änderst.

```bash
python seed_github.py --only labels,milestones  # einzelne Schritte
python seed_github.py --no-project              # ohne Board
python seed_github.py --repo kia/LearnWithMe    # anderes Repo
```

**Der Weg für Änderungen:** `backlog.json` bearbeiten → Script erneut laufen lassen. Nicht umgekehrt — Änderungen direkt in GitHub gehen beim nächsten Lauf verloren.

### Epics

| | Epic | Stories | Fokus |
|---|---|:---:|---|
| A | Identität, Konto & Abrechnung | 6 | Login, BYOK, Kosten, Löschung |
| B | Dokumenten-Import & Ingestion | 8 | PDF, URL, Struktur, Konzepte |
| C | Fragegenerierung & Qualität | 8 | Pipeline, Gates, Item-Bank |
| D | Adaptives Frage-Antwort-System | 9 | Session, Elo, FSRS, Auswahl |
| E | Echtzeit-Fehlerkorrektur & Analyse | 6 | Taxonomie, Nachfragen, Misconceptions |
| F | Clients: Web & Extension | 6 | Web-App, MV3-Extension |
| G | Plattform, Qualität & Betrieb | 13 | Modulith, Jobs, LLM-Port, Sicherheit, Evals |

### Roadmap

| Meilenstein | Definition of Done |
|---|---|
| **M0** Walking Skeleton | Text hochladen → chunken → embedden → über die API lesbar. Deployed, mit Trace. |
| **M1** Ingestion | 100-Seiten-PDF → Konzeptliste mit Belegstellen in unter 3 min, Kosten gemessen. |
| **M2** Generierung + Gates | Faktische Fehlerrate am Gold-Set ≤ 5 %. **Höchstes Risiko — großzügig planen.** |
| **M3** Adaptive Loop | `POST /attempts` p95 < 400 ms; Simulator zeigt θ-Konvergenz und Zielband-Einhaltung. |
| **M4** Clients | Beide Clients gegen dieselbe API; Extension besteht internes Store-Policy-Review. |
| **M5** Härtung & Beta | Alle NFRs gemessen und erfüllt; 20 aktive Beta-Nutzer. |
| **M6** v1.0 | Restliche Fragetypen, Freitext mit Rubric, Dozenten-Review, Export. |

**M2 ist der kritische Pfad.** Wenn die Fragenqualität dort nicht stimmt, ist alles danach wertlos.

---

## Offene Entscheidungen

Diese acht Fragen sind als Spikes im Backlog und blockieren teils den Start. Vollständig in [`docs/PLAN.md` §20](docs/PLAN.md).

| | Frage | Blockiert |
|---|---|---|
| **O-1** | Welche Inhaltssprachen in v1? | Prompts, Embedding-Wahl |
| **O-2** | Welches Embedding-Modell, welche Vektor-Dimension? | **G2** — steht in der ersten Migration, späterer Wechsel = Re-Embedding aller Chunks |
| **O-3** | FSRS: JVM-Port nutzen oder Kern selbst implementieren? | **D5** |
| **O-4** | Welche Provider erlauben Endnutzer-Keys in Drittanwendungen? | **A3** — rechtliche, keine technische Frage |
| **O-5** | Hosting und Region? | Datenschutz-Argumentation |
| **O-6** | Preismodell und Free-Limits? | **A6** |
| **O-7** | Bilder, Formeln und Tabellen in PDFs? | Marktgröße (Mathematik, Medizin) |
| **O-8** | Ein Workspace pro Nutzer oder Teams ab v1? | UI-Aufwand |

**O-1 und O-2 sollten vor M0 geklärt sein**, weil beide direkt in die erste Flyway-Migration einfließen.

---

## Konventionen

**Commits** — [Conventional Commits](https://www.conventionalcommits.org) mit Modul als Scope:

```
feat(authoring): Groundedness-Gate mit Embedding-Schwelle
fix(adaptivity): K-Faktor sank bei Teilpunkten zu schnell
docs(plan): ADR-013 zu OCR-Engine ergänzt
```

**Branches** — `<issue-nr>-<kurzbeschreibung>`, z. B. `42-groundedness-gate`.

**Pull Requests** — verlinken das Issue mit `Closes #42`. Grüne Pipeline inklusive `architectureTest` ist Voraussetzung für den Merge.

**Architekturentscheidungen** — jede nicht-triviale Entscheidung wird als ADR in `docs/PLAN.md` §7 festgehalten: Kontext, Entscheidung, **verworfene Alternativen mit Begründung**, Konsequenzen, Revisit-Trigger. Der Wert liegt in den verworfenen Alternativen — sie verhindern, dass dieselbe Diskussion in sechs Monaten von vorn beginnt.

**Prompts sind Code** — versioniert unter `prompts/<task>/vN.md`, die Version landet in `items.generated_by`. Eine Prompt-Änderung ohne Eval-Lauf ist wie ein Merge ohne Tests.

---

## Dokumentation

| Datei | Inhalt |
|---|---|
| [`docs/PLAN.md`](docs/PLAN.md) | Vollständige Analyse: Vision, Personas, 43 Anforderungen mit Akzeptanzkriterien, 15 NFRs, Domänenmodell, 12 ADRs, Schema, API, Fragetypen, adaptive Engine mit Formeln, LLM-Pipeline, Sicherheit, Teststrategie, Roadmap, Risiken |
| [`scripts/github/backlog.json`](scripts/github/backlog.json) | Backlog als strukturierte Daten |
| [`gradle/libs.versions.toml`](gradle/libs.versions.toml) | Alle Abhängigkeitsversionen |

---

## Lizenz

Noch nicht festgelegt.
