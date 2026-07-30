# LearnWithMe — Analyse, Anforderungen & Architektur

> Adaptive, KI-gestützte Lernplattform: Dokumente/Webseiten importieren → Fragen generieren → Fehler analysieren → Schwierigkeit in Echtzeit anpassen.

| | |
|---|---|
| **Status** | Entwurf v1.0 — Grundlage für Implementierung |
| **Datum** | 2026-07-28 |
| **Kontext** | Produkt / Startup-Idee |
| **Stack (gesetzt)** | Kotlin, Spring Boot, Gradle, PostgreSQL |
| **Architektur** | Modular Monolith (Spring Modulith), Web-App + Chrome-Extension parallel |
| **LLM-Strategie** | Pluggable Provider: Cloud-API, lokal (Ollama), hybrid, BYOK („Login mit eigenem AI-Account") |

---

## Inhaltsverzeichnis

1. [Executive Summary](#1-executive-summary)
2. [Produktvision & Abgrenzung](#2-produktvision--abgrenzung)
3. [Stakeholder & Personas](#3-stakeholder--personas)
4. [Anforderungsanalyse](#4-anforderungsanalyse)
5. [Domänenmodell](#5-domänenmodell)
6. [Architektur](#6-architektur)
7. [Architecture Decision Records](#7-architecture-decision-records-adrs)
8. [Datenmodell](#8-datenmodell)
9. [API-Design](#9-api-design)
10. [Fragetypen-Katalog](#10-fragetypen-katalog)
11. [Adaptive Engine](#11-adaptive-engine)
12. [LLM-Pipeline & Qualitätssicherung](#12-llm-pipeline--qualitätssicherung)
13. [AI-Account / BYOK](#13-ai-account--byok)
14. [Chrome-Extension](#14-chrome-extension)
15. [Security, Datenschutz, Compliance](#15-security-datenschutz-compliance)
16. [Observability & Kostenkontrolle](#16-observability--kostenkontrolle)
17. [Teststrategie](#17-teststrategie)
18. [Delivery-Plan](#18-delivery-plan)
19. [Risiken](#19-risiken)
20. [Offene Fragen](#20-offene-fragen)
21. [Quellen](#21-quellen)

---

## 1. Executive Summary

**Das Problem.** Lernende haben Material (Skripte, Paper, Webseiten), aber kein Feedback. Sie lesen passiv, überschätzen ihr Verständnis („Fluency Illusion") und merken erst in der Prüfung, was fehlt. Bestehende Tools lösen jeweils nur ein Teilstück: Anki hat exzellentes Scheduling, aber der Nutzer muss Karten selbst schreiben. ChatGPT generiert Fragen, aber ohne Gedächtnis, ohne Lernmodell, ohne Wiedervorlage. Quizlet/Kahoot sind statisch.

**Die Lösung.** LearnWithMe verbindet drei Schichten, die bisher niemand zusammen hat:

1. **Ingestion** — PDF/DOCX/Webseite → strukturierter, belegbarer Text + Konzeptgraph.
2. **Generation** — LLM erzeugt vielfältige Fragetypen, jede Frage ist an eine Textstelle *gebunden* (Groundedness) und durchläuft automatische Qualitätsprüfung.
3. **Adaption** — Ein Lernermodell (Elo pro Konzept) wählt in Echtzeit die nächste Frage im optimalen Schwierigkeitsband; ein Gedächtnismodell (FSRS) plant die Wiedervorlage; eine Fehlertaxonomie erkennt *warum* falsch geantwortet wurde und reformuliert gezielt.

**Der Burggraben.** Nicht das LLM (das ist Commodity), sondern der **Item-Bank-Effekt** plus das **Lernermodell**: Je mehr Nutzer eine Frage beantworten, desto besser kalibriert ihre Schwierigkeit; je länger ein Nutzer dabei ist, desto genauer sein Fähigkeitsprofil. Beides ist nicht kopierbar und wächst mit der Nutzung.

**Die zentrale technische Wette.** Wir behandeln Fragegenerierung nicht als „Prompt rein, Frage raus", sondern als **Produktionspipeline mit Qualitätstoren** (Groundedness-Check, Distraktor-Plausibilität, Duplikat-Erkennung, Kalibrierung). Ohne diese Tore ist die Fragenqualität das Produktrisiko Nr. 1 — die Forschung zu LLM-basierter Fragegenerierung nennt genau das: halluzinierte Fakten, inkonsistente Begründungen, oberflächliche Distraktoren [1][2].

**Die zentrale Produktwette.** BYOK („Login mit eigenem AI-Account") + lokaler Modus (Ollama) verschieben die variablen LLM-Kosten zum Nutzer und machen Datenschutz zum Verkaufsargument statt zum Hindernis — besonders relevant für den DACH-/EDU-Markt.

---

## 2. Produktvision & Abgrenzung

### 2.1 Vision Statement

> Für Lernende, die eigenes Material durcharbeiten müssen und kein verlässliches Feedback haben, ist **LearnWithMe** eine adaptive Lernplattform, die aus beliebigen Dokumenten und Webseiten einen personalisierten Übungspfad erzeugt. Anders als statische Quiz-Tools oder generische Chat-Assistenten modelliert LearnWithMe *was du kannst*, *was du verwechselst* und *wann du es vergisst* — und stellt genau die Frage, die dich gerade am meisten weiterbringt.

### 2.2 Wettbewerbs-Positionierung

| Produkt | Import | Auto-Generierung | Adaptivität | Spaced Repetition | Fehleranalyse | In-Browser |
|---|---|---|---|---|---|---|
| Anki | – | – | – (nur SR) | ★★★ (FSRS) | – | – |
| Quizlet | teilweise | ★★ | ★ | ★★ | – | – |
| ChatGPT/Claude | ★★★ | ★★★ | – | – | ★ (flüchtig) | ★ |
| NotebookLM | ★★★ | ★★ | – | – | – | – |
| Kahoot/Socrative | – | ★ | – | – | – | – |
| **LearnWithMe** | ★★★ | ★★★ | ★★★ | ★★★ | ★★★ | ★★★ |

**Die Lücke, die wir besetzen:** Import **und** Adaptivität **und** Gedächtnismodell in einem geschlossenen Regelkreis. Jeder Einzelbaustein existiert; die Kombination nicht.

### 2.3 Leitprinzipien (Architektur-Treiber)

| # | Prinzip | Konsequenz für die Architektur |
|---|---|---|
| P1 | **Jede Frage ist belegbar** | Jedes Item speichert `source_span` (Chunk + Zeichenoffset). Ohne Beleg keine Veröffentlichung. |
| P2 | **Das Modell ist austauschbar** | Kein Provider-SDK im Domänencode. Alles hinter einem `LlmGateway`-Port. |
| P3 | **Der Nutzer besitzt seine Daten** | Löschung kaskadiert vollständig; Export als offenes Format; lokaler Modus ohne Cloud-Call. |
| P4 | **Adaption ist erklärbar** | Kein Black-Box-Neuronetz für die Item-Auswahl. Elo/FSRS sind auditierbar und dem Nutzer anzeigbar. |
| P5 | **Token kosten Geld** | Jeder LLM-Call wird gemessen, gecacht und budgetiert. Kosten pro Dokument ist eine First-Class-Metrik. |
| P6 | **Ein Backend, viele Clients** | Web und Extension teilen dieselbe API und denselben generierten Client. Keine Client-spezifische Geschäftslogik. |

### 2.4 Explizit NICHT im Scope (v1)

- Kursverwaltung, Klassenräume, Lehrer-Dashboards (LMS-Funktionalität)
- Prüfungsmodus mit Proctoring / Zertifizierung
- Mobile Native Apps (PWA als Zwischenschritt)
- Kollaboratives Lernen / Social Features / Leaderboards
- Video- und Audio-Import (Whisper-Pipeline → v2)
- LTI-/SCORM-Integration in bestehende LMS (→ v2, aber API-Design nicht verbauen)

---

## 3. Stakeholder & Personas

### 3.1 Personas

**P1 — Studentin Lena (Primär, 70 % der Nutzung)**
Wirtschaftsinformatik, 3. Semester. Hat 12 Vorlesungsskripte à 200 Seiten und drei Wochen bis zur Klausur.
*Jobs to be done:* „Ich will wissen, welche Kapitel ich noch nicht kann, ohne alles nochmal zu lesen."
*Erfolgskriterium:* Erste sinnvolle Frage < 60 s nach Upload. Wenn das nicht klappt, kommt sie nicht wieder.
*Abbruchrisiko:* Fragen, die zu leicht („trivial"), zu wörtlich („Was steht auf Seite 12?") oder faktisch falsch sind.

**P2 — Berufstätiger Weiterbildner Marco (Sekundär)**
Cloud-Zertifizierung neben dem Job, lernt 20 min in der Bahn, liest Dokumentation im Browser.
*Jobs to be done:* „Ich lese ohnehin — mach daraus Übung, ohne dass ich die App wechseln muss."
*Erfolgskriterium:* Chrome-Extension: markieren → Frage → weiterlesen. Kein Kontextwechsel.

**P3 — Dozent Dr. Weber (Tertiär, Monetarisierungspfad)**
Will Übungsfragen zu seinem Skript, aber Qualitätskontrolle behalten.
*Jobs to be done:* „Generiere Vorschläge, ich gebe frei." → Item-Review-Workflow, Export nach QTI/Moodle.

**P4 — Datenschutzbeauftragter / IT der Hochschule (Gatekeeper)**
Blockiert Tools, die Dokumente an US-Anbieter senden.
*Jobs to be done:* „Beweise mir, dass die Skripte die EU nicht verlassen." → Lokaler Modus + AVV + EU-Hosting sind Vertriebs-Enabler, kein Nice-to-have.

### 3.2 Stakeholder-Matrix

| Stakeholder | Interesse | Einfluss | Umgang |
|---|---|---|---|
| Lernende | Qualität, Geschwindigkeit, Preis | hoch | Kernnutzer, Telemetrie + Feedback-Loop |
| Dozenten/Institutionen | Korrektheit, Compliance, Export | mittel→hoch | Review-Workflow, EU-Hosting, AVV |
| LLM-Provider | Volumen | mittel | Abstrahieren (ADR-004), BYOK reduziert Abhängigkeit |
| Google (Chrome Web Store) | Policy-Konformität MV3 | hoch (Sperrrisiko) | Minimale Permissions, keine Remote-Code-Execution |
| Rechteinhaber der Inhalte | Urheberrecht | mittel | Nur privater Nutzerbereich, kein öffentliches Teilen fremder Inhalte in v1 |

---

## 4. Anforderungsanalyse

### 4.1 Methodik

Angewandte Elicitation-Techniken und ihr jeweiliges Ergebnis:

| Technik | Anwendung | Ergebnis |
|---|---|---|
| **Zieltrichter (Vision → Epics → Stories)** | Ableitung aus den 3 Hauptfunktionen | 6 Epics, 34 Stories |
| **Persona-basierte Szenarien** | Je Persona ein End-to-End-Walkthrough | Deckt Lücken auf: Review-Workflow (P3), Offline-Modus (P4) |
| **Wettbewerbsanalyse** | Feature-Matrix (§2.2) | Differenzierung = Regelkreis, nicht Einzelfeature |
| **Qualitätsszenarien (ATAM-Stil)** | NFRs als messbare Stimulus-Response-Paare | §4.3, jede NFR mit Zahl |
| **Risiko-getriebene Analyse** | „Was tötet das Produkt?" | Fragenqualität + LLM-Kosten → eigene Kapitel (§12, §16) |
| **Constraint-Analyse** | Gesetzt: Kotlin/Spring/Gradle/Postgres; Chrome MV3 | Rahmen für ADRs |

**MoSCoW-Legende:** `M` = Must (MVP), `S` = Should (v1.0), `C` = Could (v1.x), `W` = Won't (v1)

### 4.2 Funktionale Anforderungen

#### Epic A — Identität, Konto & Abrechnung

| ID | Story | Prio | Akzeptanzkriterien (Gherkin-verkürzt) |
|---|---|---|---|
| A1 | Als Nutzer registriere ich mich per E-Mail oder Google/GitHub-SSO | M | Erfolgreicher OIDC-Flow → Nutzer + Default-Workspace angelegt; JWT gültig 15 min, Refresh 30 Tage |
| A2 | Als Nutzer melde ich mich in der Extension an, ohne Passwort erneut einzugeben | M | `launchWebAuthFlow` mit PKCE; Session in Web-App wird wiederverwendet; Token nie in `chrome.storage.local` im Klartext |
| A3 | Als Nutzer hinterlege ich meinen eigenen AI-Account/API-Key | M | Key wird envelope-verschlüsselt gespeichert; API gibt ihn *nie* zurück, nur `provider` + `keyHint` (letzte 4 Zeichen) + `lastVerifiedAt` |
| A4 | Als Nutzer sehe ich meinen Token-/Kostenverbrauch pro Dokument und Monat | S | Dashboard zeigt Input-/Output-Tokens, geschätzte Kosten, Top-5-Kostentreiber |
| A5 | Als Nutzer lösche ich mein Konto vollständig | M | Innerhalb 24 h: alle Dokumente, Chunks, Embeddings, Items, Attempts gelöscht; Nachweis per Audit-Log-Eintrag |
| A6 | Als Nutzer nutze ich ein Gratis-Kontingent ohne eigenen Key | S | Plattform-Key mit hartem Monatslimit; bei Überschreitung klarer Upgrade-/BYOK-Hinweis, kein stiller Fehler |

#### Epic B — Dokumenten-Import & Ingestion

| ID | Story | Prio | Akzeptanzkriterien |
|---|---|---|---|
| B1 | Als Nutzer lade ich ein PDF hoch (bis 100 MB / 1000 Seiten) | M | Upload → Job-ID sofort; Fortschritt per SSE; Status: `UPLOADED→EXTRACTING→CHUNKING→INDEXING→READY\|FAILED` |
| B2 | Als Nutzer importiere ich eine Webseite per URL | M | Readability-Extraktion; Paywall/JS-Only wird erkannt und als `PARTIAL` markiert statt Müll zu speichern |
| B3 | Als Nutzer importiere ich die aktuelle Seite aus der Extension | M | Extension sendet extrahiertes DOM-Fragment (nicht die URL) → funktioniert auch hinter Login |
| B4 | Als Nutzer importiere ich DOCX/Markdown/Plaintext/EPUB | S | Gleiche Pipeline über Tika |
| B5 | Als Nutzer sehe ich, wenn mein PDF gescannt ist, und kann OCR anstoßen | S | Textdichte < Schwellwert → `needsOcr=true`; OCR opt-in (Kosten/Zeit sichtbar) |
| B6 | Als Nutzer sehe ich die erkannte Struktur (Kapitel/Abschnitte) und kann Bereiche ausschließen | S | Baumansicht; Ausschluss von Literaturverzeichnis/Anhang möglich |
| B7 | Als System dedupliziere ich identische Uploads | S | SHA-256 über Rohdatei; identische Datei desselben Nutzers → Wiederverwendung statt Neuextraktion |
| B8 | Als Nutzer sehe ich die extrahierten Kernkonzepte des Dokuments | M | Konzeptliste mit Häufigkeit + Belegstellen; manuell editierbar |

#### Epic C — Fragegenerierung (Authoring)

| ID | Story | Prio | Akzeptanzkriterien |
|---|---|---|---|
| C1 | Als System generiere ich zu jedem Konzept Fragen verschiedener Typen | M | ≥ 5 Typen (§10); Verteilung konfigurierbar; jede Frage mit `source_span` |
| C2 | Als System verwerfe ich Fragen, die nicht durch den Quelltext gedeckt sind | M | Groundedness-Gate: LLM-Judge + Embedding-Similarity < Schwelle → `REJECTED_UNGROUNDED`, kein Nutzerkontakt |
| C3 | Als System verwerfe ich strukturell defekte Fragen | M | Deterministische Validatoren: genau 1 korrekte Antwort bei MC-Single, keine Dubletten in Optionen, keine „Alle der genannten", Optionslängen-Varianz unter Schwelle (verhindert „längste Antwort = richtig") |
| C4 | Als System erkenne ich Duplikate zu bestehenden Items | M | Embedding-Cosine > 0.93 gegen Item-Bank desselben Dokuments → verworfen |
| C5 | Als Nutzer melde ich eine schlechte Frage | M | 1-Klick-Report mit Grund (falsch / unklar / trivial / nicht im Text); Item wird sofort aus meiner Rotation genommen |
| C6 | Als Nutzer generiere ich gezielt mehr Fragen zu einem schwachen Konzept | S | „Mehr üben" auf Konzeptebene → On-Demand-Generierung |
| C7 | Als Dozent prüfe und gebe ich Items frei | C | Review-Queue mit `DRAFT→APPROVED→PUBLISHED`; Bulk-Aktionen |
| C8 | Als System generiere ich Erklärungen zu *jeder* Option, nicht nur zur richtigen | M | Jeder Distraktor hat `rationale` (warum falsch) — Grundlage der Fehleranalyse |

#### Epic D — Adaptives Frage-Antwort-System

| ID | Story | Prio | Akzeptanzkriterien |
|---|---|---|---|
| D1 | Als Nutzer starte ich eine Lernsession zu einem Dokument | M | Session mit Zielvorgabe (Zeit oder Anzahl); erste Frage < 500 ms nach Start |
| D2 | Als System wähle ich die nächste Frage nach meinem Fähigkeitsstand | M | Auswahl-Policy zielt auf Erfolgswahrscheinlichkeit im Band 0.70–0.85 (§11.3) |
| D3 | Als System aktualisiere ich Fähigkeit und Item-Schwierigkeit nach jeder Antwort | M | Elo-Update mit unsicherheitsabhängigem K; < 50 ms serverseitig |
| D4 | Als Nutzer erhalte ich sofort Feedback mit Begründung und Beleg | M | Richtig/falsch + `rationale` + Zitat aus Quelle mit Seitenzahl/Anker |
| D5 | Als System plane ich Wiedervorlagen nach einem Gedächtnismodell | M | FSRS-Scheduling pro Konzept-Karte; Fälligkeiten im Kalender sichtbar |
| D6 | Als Nutzer kann ich eine Frage überspringen / als unklar markieren | M | Skip zählt nicht als Fehler, fließt aber in Item-Qualitätssignal |
| D7 | Als Nutzer sehe ich meinen Fortschritt pro Konzept | M | Beherrschungsgrad 0–100 % je Konzept, Verlauf über Zeit |
| D8 | Als Nutzer lerne ich offline weiter (Extension/PWA) | C | Vorab-Download der nächsten N Items; Attempts werden nachträglich synchronisiert (Konfliktstrategie: append-only) |
| D9 | Als System mische ich Fragetypen zum selben Konzept | S | Gleicher Inhalt in MC, Reihenfolge, Wahr/Falsch → Transfer statt Auswendiglernen |

#### Epic E — Echtzeit-Fehlerkorrektur & -analyse

| ID | Story | Prio | Akzeptanzkriterien |
|---|---|---|---|
| E1 | Als System klassifiziere ich jeden Fehler nach Taxonomie | M | Kategorien: Faktenwissen / Begriffsverwechslung / Konzeptuelles Missverständnis / Prozedural / Flüchtigkeit / Wissenslücke (§11.5) |
| E2 | Als Nutzer bekomme ich bei einem Fehler eine Nachfrage, die das Missverständnis prüft | M | Follow-up-Item wird binnen 2 s aus derselben Quellstelle erzeugt oder aus der Bank gewählt |
| E3 | Als System erkenne ich wiederkehrende Missverständnisse | S | ≥ 3 Fehler derselben Kategorie im selben Konzept → Misconception-Flag + gezielte Intervention |
| E4 | Als Nutzer bewerte ich meine Freitextantwort gegen ein Rubric | S | LLM-Bewertung mit Teilpunkten + konkreter Verbesserungshinweis; Rubric ist dem Nutzer sichtbar |
| E5 | Als Nutzer erhalte ich einen Wochenreport meiner Schwachstellen | C | E-Mail/In-App: Top-3-Lücken, Trend, empfohlener Fokus |
| E6 | Als System formuliere ich falsch beantwortete Fragen um statt sie wörtlich zu wiederholen | M | Paraphrase-Variante beim Wiedersehen; gleiche Konzept-ID, anderes `stem` |

#### Epic F — Clients (Web & Extension)

| ID | Story | Prio | Akzeptanzkriterien |
|---|---|---|---|
| F1 | Web-App: Bibliothek, Import, Lernsession, Fortschritt | M | Responsive; Tastaturbedienung komplett (1–9 für Optionen, Enter, Space) |
| F2 | Extension: Side Panel mit Session zur aktuellen Seite | M | MV3, Side Panel API; Öffnen ohne Reload |
| F3 | Extension: Markierten Text in Frage verwandeln | M | Kontextmenü „Frage erzeugen" auf Selektion |
| F4 | Extension: minimale Permissions | M | Nur `activeTab`, `sidePanel`, `storage`, `identity`; **kein** `<all_urls>` im Host-Permission-Block |
| F5 | Barrierefreiheit | S | WCAG 2.2 AA für die Session-Ansicht; Screenreader-Labels für alle Fragetypen |
| F6 | Mehrsprachigkeit | S | UI de/en; Fragen in der Sprache des Quelldokuments (automatisch erkannt), umschaltbar |

### 4.3 Nicht-funktionale Anforderungen

Formuliert als Qualitätsszenarien: *Stimulus → Umgebung → Antwort → Metrik*.

| ID | Kategorie | Szenario | Metrik (v1-Ziel) |
|---|---|---|---|
| N1 | Latenz | Nutzer beantwortet Frage → nächste Frage erscheint | p95 < 400 ms (ohne LLM-Call im kritischen Pfad) |
| N2 | Latenz | Nutzer lädt 50-seitiges PDF hoch → erste Frage verfügbar | p95 < 60 s |
| N3 | Latenz | Nutzer lädt 500-seitiges PDF hoch → vollständig indexiert | p95 < 10 min, Teilergebnisse ab 60 s nutzbar |
| N4 | Durchsatz | 200 gleichzeitige Lernsessions | CPU < 60 %, keine Fehler; 1 App-Instanz (2 vCPU/4 GB) |
| N5 | Verfügbarkeit | Lernen muss möglich sein, auch wenn der LLM-Provider ausfällt | Session-Betrieb aus Item-Bank funktioniert ohne LLM; nur Generierung/Freitext-Bewertung degradiert |
| N6 | Qualität | Von 100 generierten Items sind ≤ 5 faktisch falsch | Gemessen an Gold-Set (§17.4), Gate im CI |
| N7 | Qualität | Nutzer-Report-Rate pro ausgelieferter Frage | < 3 % |
| N8 | Kosten | LLM-Kosten pro 100-Seiten-Dokument (Plattform-Key) | < 0,50 € bei Standard-Routing |
| N9 | Sicherheit | Ein Nutzer darf niemals Daten eines anderen sehen | Tenant-Filter in jeder Query + PostgreSQL RLS als zweite Verteidigungslinie; Test erzwingt es |
| N10 | Datenschutz | Dokumente verlassen die EU nicht (außer bei explizit gewähltem Nicht-EU-Provider) | Provider-Region als Pflichtattribut; UI zeigt Datenfluss vor dem ersten Call |
| N11 | Wartbarkeit | Modulgrenzen können nicht versehentlich verletzt werden | `ApplicationModules.verify()` + ArchUnit im Build; Build bricht bei Verstoß |
| N12 | Portabilität | Wechsel des LLM-Providers | Keine Änderung außerhalb des Moduls `ai`; nachgewiesen durch Test mit 2 Providern |
| N13 | Beobachtbarkeit | Jeder LLM-Call ist nachvollziehbar | Trace mit Prompt-Hash, Modell, Tokens, Kosten, Latenz, Ergebnis-Gate |
| N14 | Datenintegrität | Kein Verlust von Lernfortschritt | Attempts sind append-only; PITR-Backup, RPO ≤ 5 min, RTO ≤ 1 h |
| N15 | Skalierbarkeit | Item-Bank wächst auf 10 Mio. Items | Vektorsuche p95 < 100 ms mit HNSW-Index |

### 4.4 Constraints & Annahmen

**Constraints (nicht verhandelbar)**

- C-1: Kotlin + Spring Boot + Gradle + PostgreSQL (Vorgabe)
- C-2: Chrome Manifest V3 — kein Remote-Code, Service Worker statt Background Page, begrenzte Lebensdauer des Workers
- C-3: DSGVO — Auftragsverarbeitung, Löschkonzept, Datenminimierung
- C-4: Ein-Personen-/Kleinteam-Entwicklung → Betriebsaufwand ist ein Architekturkriterium
- C-5: Urheberrecht — Nutzerinhalte bleiben privat; keine öffentliche Item-Bank aus fremdem Material

**Annahmen (zu validieren)**

- A-1: Nutzer akzeptieren 30–60 s Wartezeit beim Erstimport, wenn Fortschritt sichtbar ist. → *Validieren mit ersten 20 Nutzern.*
- A-2: Ein relevanter Teil der Zielgruppe hat oder beschafft einen eigenen AI-Account. → *Wenn falsch: Plattform-Key + Abo wird zum Hauptmodell, Kostenkontrolle (§16) wird kritisch.*
- A-3: Elo-basierte Adaption ist ohne Vorkalibrierung ausreichend genau. → *Gestützt durch Literatur: Elo erreicht bereits bei n=5 Antworten r≈0.70, bei n=50 r≈0.91 gegenüber Referenzwerten [3][4].*
- A-4: Lokale Modelle (Ollama, 7–14 B) liefern brauchbare Fragen. → *Riskant. Structured Output ist bei kleinen Modellen unzuverlässig; Gates (§12.4) fangen das ab, senken aber die Ausbeute. Früh messen.*

### 4.5 Priorisierung: Was ist der MVP?

**MVP-Definition:** Ein Nutzer lädt ein PDF hoch, bekommt binnen einer Minute Fragen in mindestens vier Typen, beantwortet sie, sieht bei Fehlern eine belegte Erklärung, und die nächste Frage passt sich seinem Stand an. Web + Extension. Eigener API-Key oder Gratis-Kontingent.

Alles mit `M` markierte gehört dazu — das sind 24 Stories. `S` und `C` folgen in v1.0/v1.x.

---

## 5. Domänenmodell

### 5.1 Ubiquitous Language

| Begriff | Definition | Abgrenzung |
|---|---|---|
| **Source** | Vom Nutzer importiertes Original (PDF, URL, Text) | Nicht der extrahierte Text |
| **Document** | Verarbeitete Fassung einer Source mit Struktur | 1:1 zu Source, aber neu erzeugbar |
| **Chunk** | Zusammenhängender Textabschnitt mit stabiler ID und Offsets | Einheit für Retrieval und Beleg |
| **Concept** | Lernziel/Wissenseinheit („Normalisierung 3NF") | Nicht = Kapitel; ein Kapitel enthält viele Concepts |
| **Item** | Eine konkrete Frage inkl. Optionen, Lösung, Begründungen | Nicht = Aufgabe; Item ist wiederverwendbar |
| **ItemVariant** | Paraphrase eines Items zum selben Concept | Verhindert Auswendiglernen der Formulierung |
| **Attempt** | Eine Beantwortung eines Items durch einen Learner | Unveränderlich (append-only) |
| **Session** | Zusammenhängende Folge von Attempts | Hat Ziel, Dauer, Zusammenfassung |
| **LearnerState** | Fähigkeit θ pro Concept + Unsicherheit | Wandert kontinuierlich |
| **MemoryCard** | FSRS-Zustand (Difficulty, Stability, Retrievability) pro Learner×Concept | Getrennt von θ: θ = Können, Card = Erinnern |
| **Misconception** | Wiederkehrendes, benanntes Fehlmuster | Nicht = einzelner Fehler |
| **AiCredential** | Verschlüsselter Zugang zu einem LLM-Provider | Plattform- oder Nutzer-eigen |

### 5.2 Aggregate & Invarianten

```
┌─────────────────────────── content ────────────────────────────┐
│  Source (AR)                                                    │
│   └─ Document                                                   │
│        ├─ Section*   (Struktur, hierarchisch)                   │
│        └─ Chunk*     (Text + Offsets + Embedding-Ref)           │
│  INV: Document.status=READY ⟹ ≥1 Chunk mit Embedding            │
└─────────────────────────────────────────────────────────────────┘
                │ ConceptsExtracted (Event)
                ▼
┌─────────────────────────── knowledge ──────────────────────────┐
│  Concept (AR)  ── evidence ──▶ Chunk-Referenzen                 │
│   └─ ConceptRelation* (prerequisite_of, part_of, related_to)    │
│  INV: Concept hat ≥1 Belegstelle; Prerequisite-Graph azyklisch  │
└─────────────────────────────────────────────────────────────────┘
                │ ConceptReady (Event)
                ▼
┌─────────────────────────── authoring ──────────────────────────┐
│  Item (AR)                                                      │
│   ├─ payload (typ-spezifisch, JSONB)                            │
│   ├─ sourceSpan (chunkId + from + to)   ← P1                    │
│   ├─ rationale je Option                ← C8                    │
│   └─ QualityReport (Gate-Ergebnisse)                            │
│  INV: status=PUBLISHED ⟹ alle Gates bestanden ∧ sourceSpan ≠ ∅  │
│  INV: Typ MC_SINGLE ⟹ genau 1 correct=true                      │
└─────────────────────────────────────────────────────────────────┘
                │ ItemPublished (Event)
                ▼
┌────────────── assessment ──────────┬──────── adaptivity ────────┐
│  Session (AR)                      │  LearnerState (AR)         │
│   └─ Attempt*  (append-only)       │   ├─ θ, σ je Concept       │
│  INV: Attempt unveränderlich       │   └─ MemoryCard je Concept │
│  INV: Session.itemCount = |Attempt|│  INV: θ ∈ [-4, +4] (logit) │
└────────────────────────────────────┴────────────────────────────┘
                │ AttemptRecorded (Event)
                ▼
┌─────────────────────────── analytics ──────────────────────────┐
│  ErrorEvent → Misconception (AR) → Intervention                 │
└─────────────────────────────────────────────────────────────────┘
```

**Die wichtigste Modellentscheidung:** `Concept` ist die Achse, um die sich alles dreht — nicht `Document` und nicht `Item`. Fähigkeit, Gedächtnis, Fehler und Fortschritt hängen am Concept. Dadurch überträgt sich Wissen zwischen Dokumenten (dasselbe Concept in zwei Skripten = ein Lernstand) und Items bleiben austauschbar.

---

## 6. Architektur

### 6.1 Kontextdiagramm (C4 Level 1)

```
        ┌──────────┐   ┌──────────────┐   ┌────────────┐
        │ Lernende │   │ Dozent (v1.x)│   │ Admin      │
        └────┬─────┘   └──────┬───────┘   └─────┬──────┘
             │                │                 │
   ┌─────────┴────────────────┴─────────────────┴───────────┐
   │                                                        │
   │  ┌──────────────┐            ┌────────────────────┐    │
   │  │  Web-App     │            │  Chrome-Extension  │    │
   │  │  (React/TS)  │            │  (MV3, Side Panel) │    │
   │  └──────┬───────┘            └─────────┬──────────┘    │
   │         └────────── HTTPS/JSON ────────┘               │
   │                        │                               │
   │              ┌─────────▼──────────┐                     │
   │              │  LearnWithMe API   │  Kotlin/Spring Boot │
   │              │  Modular Monolith  │                     │
   │              └─────────┬──────────┘                     │
   └────────────────────────┼────────────────────────────────┘
                            │
    ┌──────────┬────────────┼─────────────┬───────────────┐
    ▼          ▼            ▼             ▼               ▼
┌────────┐ ┌────────┐ ┌───────────┐ ┌──────────┐ ┌──────────────┐
│Postgres│ │Object  │ │ LLM-      │ │ IdP      │ │ KMS / Vault  │
│+pgvector│ │Storage │ │ Provider  │ │ (OIDC)   │ │ (Key-Wrap)   │
│        │ │(S3)    │ │ Cloud/    │ │          │ │              │
│        │ │        │ │ Ollama    │ │          │ │              │
└────────┘ └────────┘ └───────────┘ └──────────┘ └──────────────┘
```

### 6.2 Container (C4 Level 2)

| Container | Technologie | Verantwortung | Skalierung |
|---|---|---|---|
| **API-App** | Kotlin 2.x, Spring Boot 3.5+, Spring Modulith 2.x | Gesamte Geschäftslogik, REST + SSE | horizontal, stateless |
| **Worker** | Gleiches Artefakt, Profil `worker` | Ingestion, Generierung, Embedding — alles Langlaufende | horizontal, unabhängig von API |
| **PostgreSQL** | PG 17 + `pgvector` | Relationale Daten, Vektoren, Job-Queue, Outbox | vertikal, dann Read-Replicas |
| **Object Storage** | S3-kompatibel (MinIO lokal / Hetzner-S3 prod) | Originaldateien | – |
| **Web-Frontend** | React + TypeScript + Vite | UI | CDN |
| **Extension** | TypeScript, MV3, Side Panel | In-Browser-Client | – |

> **Ein Deployment-Artefakt, zwei Rollen.** API und Worker sind dasselbe JAR mit unterschiedlichem Spring-Profil. Das halbiert den Betriebsaufwand (C-4) und erlaubt trotzdem, die CPU-intensive Ingestion getrennt zu skalieren.

### 6.3 Modulschnitt (Spring Modulith)

```
de.learnwithme
├── shared/            # Value Objects, Errors, IDs, Result-Typen  (von allen nutzbar)
├── identity/          # User, Workspace, Tenant, Auth, Plan, Quota
├── ai/                # ⭐ LLM-Abstraktion: Gateway, Router, Credentials, Cost
├── content/           # Source, Ingestion-Pipeline, Extraction, Chunking
├── knowledge/         # Concept-Extraktion, Concept-Graph, Embeddings, Retrieval
├── authoring/         # Item-Generierung, Quality-Gates, Item-Bank
├── assessment/        # Session, Item-Delivery, Grading, Feedback
├── adaptivity/        # Elo-Lernermodell, FSRS-Scheduler, Selection-Policy
├── analytics/         # Fehlertaxonomie, Misconceptions, Reports
└── platform/          # Jobs, Outbox, Storage, Observability, Config
```

**Abhängigkeitsregeln** (erzwungen durch `ApplicationModules.verify()`, N11):

```
identity  ──▶ shared
ai        ──▶ shared, identity            (braucht Credentials des Nutzers)
content   ──▶ shared, ai, platform
knowledge ──▶ shared, ai, content(API)
authoring ──▶ shared, ai, knowledge(API), content(API)
assessment──▶ shared, authoring(API), adaptivity(API), ai
adaptivity──▶ shared                       ⚠ KEINE Abhängigkeit zu ai — reine Mathematik
analytics ──▶ shared, ai                   (nur lesend via Events)
```

Zwei Regeln, die ich besonders betonen würde:

1. **`adaptivity` kennt kein LLM.** Die Adaptionslogik ist deterministische Mathematik. Sie ist damit einzeln testbar, offline simulierbar (§17.5) und erklärbar (P4). Sobald man LLM-Calls in die Item-Auswahl lässt, verliert man Latenz (N1), Reproduzierbarkeit und Auditierbarkeit.
2. **Modulübergreifend nur Events oder explizite `api`-Pakete.** Jedes Modul hat `api/` (öffentlich: DTOs, Ports) und `internal/` (Rest). Spring Modulith setzt genau das durch und dokumentiert die Struktur automatisch.

**Interne Paketstruktur je Modul** (Beispiel `authoring`):

```
authoring/
├── ItemGenerationApi.kt        # öffentlicher Port (Named Interface)
├── events/ItemPublished.kt     # öffentliche Events
└── internal/
    ├── domain/     Item.kt, ItemPayload.kt (sealed), QualityReport.kt
    ├── generation/ GenerationPipeline.kt, prompts/
    ├── quality/    GroundednessGate.kt, StructuralGate.kt, DuplicateGate.kt
    ├── persistence/ItemRepository.kt, ItemEntity.kt
    └── web/        ItemController.kt
```

### 6.4 Kern-Flow 1: Dokument-Import

```
Client                API              Worker             LLM        DB
  │  POST /sources     │                 │                 │          │
  ├───────────────────▶│                 │                 │          │
  │                    │ Source(PENDING) ─────────────────────────────▶│
  │                    │ enqueue(IngestJob)                            │
  │  202 {sourceId}    │                 │                            │
  │◀───────────────────┤                 │                            │
  │  GET /sources/{id}/events (SSE)      │                            │
  ├─────────────────────────────────────▶│                            │
  │                    │                 │ 1 Extract (Tika/PDFBox)     │
  │  ◀── EXTRACTING ───────────────────  │                            │
  │                    │                 │ 2 Structure + Chunk         │
  │  ◀── CHUNKING ─────────────────────  │ 3 Embed (Batch) ──────────▶│
  │                    │                 │ 4 upsert chunks+vectors ───▶│
  │  ◀── INDEXING ─────────────────────  │ 5 Concepts extrahieren ───▶│
  │                    │                 │ 6 Items generieren ───────▶│
  │                    │                 │ 7 Quality-Gates             │
  │  ◀── READY ────────────────────────  │                            │
```

**Wichtige Eigenschaft:** Schritt 6 läuft **konzeptweise inkrementell**. Sobald das erste Concept Items hat, wird `DocumentPartiallyReady` gefeuert und der Nutzer kann starten (N2: erste Frage < 60 s, auch bei 500 Seiten).

### 6.5 Kern-Flow 2: Adaptive Antwortschleife (der kritische Pfad)

```
POST /sessions/{id}/attempts  { itemId, response, elapsedMs }
   │
   ├─ 1  Grading (deterministisch)                       ~1 ms
   │       └─ Freitext? → LLM-Rubric (async, optimistisches Feedback)
   ├─ 2  Attempt persistieren (append-only)              ~3 ms
   ├─ 3  Elo-Update θ_user, d_item                       <1 ms
   ├─ 4  FSRS-Update der MemoryCard                      <1 ms
   ├─ 5  Fehlerklassifikation                            
   │       └─ Distraktor bekannt? → Kategorie aus rationale  (0 ms, kein LLM!)
   │       └─ sonst → Event für asynchrone LLM-Analyse
   ├─ 6  Nächstes Item wählen (Kandidaten aus Bank)      ~15 ms
   └─ 7  Response: Feedback + nächstes Item              p95 < 400 ms ✓
   
   Asynchron (Event): AttemptRecorded → analytics, Misconception-Erkennung,
                      ggf. On-Demand-Generierung einer Paraphrase (E6)
```

**Der entscheidende Trick für N1:** Kein LLM-Call im synchronen Pfad. Das ist möglich, weil C8 (Rationale pro Distraktor) schon zur Generierungszeit erzeugt wurde. Die „Echtzeit-Fehlerkorrektur" fühlt sich für den Nutzer wie eine Live-KI-Analyse an, ist aber vorberechnet. LLM-Calls passieren nur, wenn wirklich Neues nötig ist (Freitext-Bewertung, neue Paraphrase) — und dann asynchron mit optimistischem UI.


---

## 7. Architecture Decision Records (ADRs)

### ADR-001 — Modular Monolith mit Spring Modulith statt Microservices

**Status:** Akzeptiert
**Kontext:** Kleines Team (C-4), unklare Domänengrenzen zu Beginn, aber mittelfristig unterschiedliche Skalierungsprofile (Ingestion CPU-lastig, Session-Loop latenzkritisch).
**Entscheidung:** Ein Deployment-Artefakt, Modulgrenzen im Code erzwungen durch Spring Modulith 2.x + ArchUnit. Trennung nur über Spring-Profile (`api` / `worker`).
**Alternativen:**
- *Microservices ab Tag 1* — verworfen: verteilte Transaktionen, Betriebsaufwand, Grenzen sind noch nicht bekannt. Ein falscher Schnitt kostet in Microservices ein Vielfaches.
- *Unstrukturierter Monolith* — verworfen: ohne erzwungene Grenzen entsteht in 12 Monaten ein Ball of Mud, und die spätere Extraktion wird unmöglich.
**Konsequenzen:** (+) Refactoring über Modulgrenzen bleibt billig; (+) `ApplicationModules.verify()` bricht den Build bei Verstößen; (+) Extraktion eines Moduls in einen Service ist später mechanisch, weil Kommunikation bereits über Events läuft. (−) Ein Speicherleck betrifft alles; (−) Disziplin nötig, damit `internal/` nicht umgangen wird.
**Revisit-Trigger:** Wenn Ingestion-Last die API-Latenz trotz Profiltrennung beeinflusst, oder Team > 8 Entwickler.

### ADR-002 — PostgreSQL als einzige Datenbank (inkl. Vektoren, Queue, Outbox)

**Status:** Akzeptiert
**Kontext:** Wir brauchen relationale Daten, Vektorsuche, eine Job-Queue und ein Event-Outbox. Naheliegend wären Postgres + Qdrant + Redis + Kafka.
**Entscheidung:** Alles in PostgreSQL 17: `pgvector` mit HNSW-Index für Embeddings, DB-gestützte Job-Queue, Modulith-Event-Publication-Registry als Outbox, `JSONB` für Item-Payloads.
**Alternativen:**
- *Dedizierte Vektor-DB (Qdrant/Weaviate)* — verworfen für v1: bei erwarteten < 10 Mio. Vektoren ist pgvector mit HNSW schnell genug (N15), und wir sparen eine Komponente, eine Konsistenzgrenze und einen Betriebsvertrag. Wichtiger noch: Vektorsuche gefiltert nach `tenant_id` und `document_id` ist in Postgres ein normales `WHERE` — in externen Vektor-DBs oft der schmerzhafteste Teil.
- *Kafka für Events* — verworfen: Modulith-Events + Outbox reichen bei einem Deployment; Kafka bringt Betriebslast ohne aktuellen Nutzen.
**Konsequenzen:** (+) Eine Backup-Strategie, eine Transaktionsgrenze, ein Monitoring; (+) Vektorsuche und Metadaten-Filter in einer Query. (−) Postgres wird zum Single Point of Contention; (−) HNSW-Indexaufbau ist speicher- und zeitintensiv, muss beim Bulk-Import beachtet werden.
**Revisit-Trigger:** > 20 Mio. Vektoren oder p95-Vektorsuche > 100 ms.

### ADR-003 — Kotlin als Hauptsprache, Java-Interop erlaubt

**Status:** Akzeptiert
**Kontext:** Vorgabe C-1 lässt beides zu.
**Entscheidung:** Kotlin 2.x. Sealed Interfaces für Fragetypen (§10) und Pipeline-Ergebnisse, Data Classes als DTOs, Coroutines nur an den I/O-Rändern (LLM-Calls, Batch-Embedding) — der Rest bleibt blockierend und einfach.
**Begründung:** Der Item-Typ ist ein geschlossener algebraischer Datentyp. Kotlins `sealed interface` + `when` ohne `else` macht das Hinzufügen eines Fragetyps zu einem Compiler-Fehler an jeder Stelle, die ihn behandeln muss. In Java ginge das mit Sealed Classes auch, aber die Serialisierungs- und Null-Ergonomie ist in Kotlin deutlich besser. Relevanter Praxispunkt: Spring AI generiert JSON Schemas aus Kotlin Data Classes und behandelt nullable/Default-Properties korrekt als optional [5].
**Konsequenzen:** (+) Weniger Boilerplate, sicherere Modellierung. (−) Kotlin-Spezifika bei JPA (Open-Klassen, No-Arg-Plugin) müssen konfiguriert werden.

### ADR-004 — LLM-Zugriff hinter einem eigenen Port, Spring AI als Adapter

**Status:** Akzeptiert
**Kontext:** Anforderung: Cloud, lokal, hybrid, BYOK — alles pluggable (N12).
**Entscheidung:** Eigene Ports im Modul `ai`:

```kotlin
interface LlmGateway {
    suspend fun <T : Any> complete(req: StructuredRequest<T>): LlmResult<T>
    suspend fun completeText(req: TextRequest): LlmResult<String>
}
interface EmbeddingGateway { suspend fun embed(texts: List<String>): List<FloatArray> }
interface ModelRouter { fun resolve(task: LlmTask, ctx: TenantContext): ModelBinding }
```

Spring AI ist die *Implementierung* dahinter (`ChatClient`, `entity()`, `PgVectorStore`), niemals der Typ in Signaturen außerhalb von `ai/internal`.
**Alternativen:**
- *Spring AI direkt überall* — verworfen: Spring AI ist ein junges, sich schnell änderndes Framework. Ein Breaking Change dürfte nicht durch die halbe Codebasis wandern. Die Upgrade-Notes zeigen bereits reale Bruchstellen (z. B. Umstellung der JSON-Schema-Generierung im `BeanOutputConverter`) [5].
- *Eigene HTTP-Clients pro Provider* — verworfen: zu viel Arbeit; Spring AI löst Structured Output, Tool Calling, Retry und Observability bereits.
**Konsequenzen:** (+) Provider-Wechsel und Framework-Upgrades bleiben lokal; (+) Tests können `LlmGateway` trivial faken. (−) Eine zusätzliche Abstraktionsschicht; (−) Provider-spezifische Features (z. B. Prompt Caching) müssen bewusst durchgereicht werden.

### ADR-005 — Elo für Echtzeit-Adaption, IRT frühestens später

**Status:** Akzeptiert
**Kontext:** Wir brauchen Item-Auswahl in < 50 ms (N1), bei ständig neuen, unkalibrierten Items (Cold Start ist der Normalfall, nicht die Ausnahme — jedes neu generierte Item ist neu).
**Entscheidung:** Elo-Rating mit unsicherheitsabhängigem K-Faktor. θ (Fähigkeit) und d (Schwierigkeit) werden nach jeder Antwort aktualisiert (§11).
**Begründung:** Klassische IRT-Kalibrierung braucht große Vorstichproben pro Item — bei kontinuierlich generierten Items unmöglich. Elo erreicht laut Literatur bereits bei n≈5 Antworten eine Korrelation von ~0.70 zu Referenzschwierigkeiten und ~0.91 bei n≈50, und ist rechnerisch trivial [3][4]. Genau dieser Cold-Start-Vorteil ist für uns entscheidend.
**Alternativen:**
- *IRT (2PL/3PL)* — verworfen für v1: Rechenaufwand und Datenbedarf. Vorgemerkt als Offline-Rekalibrierung für Items mit > 200 Antworten.
- *Bayesian Knowledge Tracing* — verworfen: modelliert Skill-Mastery binär, passt schlechter zu graduierter Schwierigkeitssteuerung.
- *Deep Knowledge Tracing* — verworfen: nicht erklärbar (P4), Datenbedarf, Latenz.
**Konsequenzen:** (+) Funktioniert ab dem ersten Nutzer; (+) erklärbar. (−) K-Faktor muss getunt werden; (−) Elo entspricht implizit einem Rasch-Modell (keine Trennschärfe-/Rateparameter) — bei MC-Fragen mit 4 Optionen ist die Ratewahrscheinlichkeit von 25 % nicht modelliert. **Mitigation:** Antwortzeit als zusätzliches Signal, und Konfidenzabfrage bei kritischen Items.
**Revisit-Trigger:** Item-Bank mit > 200 Antworten je Item → Offline-2PL-Kalibrierung, θ bleibt Elo-getrieben.

### ADR-006 — FSRS für Wiedervorlage, getrennt vom Fähigkeitsmodell

**Status:** Akzeptiert
**Kontext:** „Können" und „Erinnern" sind verschiedene Dinge. Elo sagt, wie schwer eine Frage für dich *jetzt* ist; es sagt nicht, wann du es vergisst.
**Entscheidung:** Pro Learner×Concept eine `MemoryCard` mit dem DSR-Modell (Difficulty, Stability, Retrievability) nach FSRS. Fälligkeit steuert, *ob* ein Concept drankommt; Elo steuert, *welches Item* dazu.
**Begründung:** SM-2 hat kein Gedächtnismodell, sondern feste Multiplikatoren. FSRS modelliert die Vergessenskurve explizit und ist auf sehr großen Review-Datensätzen trainiert; FSRS-6 (Ende 2025) wurde auf ~700 Mio. Reviews gefittet und ist in Anki der Default für neue Profile [6][7].
**Alternativen:** *SM-2* — verworfen (weniger effizient bei gleicher Retention); *Leitner-Boxen* — zu grob; *eigenes Modell* — kein Grund, ein gut evaluiertes Verfahren neu zu erfinden.
**Konsequenzen:** (+) Deutlich weniger Reviews bei gleicher Behaltensleistung; (+) Parameter später pro Nutzer optimierbar. (−) Implementierungsaufwand; verfügbare JVM-Ports sind zu prüfen, sonst Eigenimplementierung des Kernalgorithmus (überschaubar, ~200 LOC + Parametertabelle).
**Offen:** Konkrete FSRS-Version und Lizenz der Referenzimplementierung prüfen (§20, O-3).

### ADR-007 — Fragetypen als Sealed Interface + JSONB-Payload

**Status:** Akzeptiert
**Kontext:** MC-Single, MC-Multi, Wahr/Falsch, Reihenfolge, Zuordnung, Lückentext, Kurzantwort, Numerisch … mit sehr unterschiedlicher Struktur.
**Entscheidung:** Ein `items`-Table mit gemeinsamen Spalten (`id, concept_id, type, stem, difficulty, source_span, status`) + `payload JSONB` für den typspezifischen Teil. Im Code: `sealed interface ItemPayload` mit polymorpher Jackson-Serialisierung über `type`.
**Alternativen:**
- *Table-per-Type* — verworfen: Joins bei jeder Item-Auswahl, Migration bei jedem neuen Typ.
- *Vollständig generisches Key-Value-Schema (EAV)* — verworfen: unlesbar, nicht validierbar.
**Konsequenzen:** (+) Neuer Fragetyp = neue Sealed-Subklasse + Validator + Renderer, keine Migration; (+) `when` ohne `else` erzwingt Vollständigkeit an jeder Behandlungsstelle. (−) JSONB ist nicht schema-validiert durch die DB → **Mitigation:** JSON-Schema-Validierung in der Anwendungsschicht + Constraint auf `type`.

### ADR-008 — Groundedness als hartes Qualitätstor

**Status:** Akzeptiert
**Kontext:** Größtes Produktrisiko sind falsche Fragen (N6). Die Forschungslage zu LLM-Fragegenerierung benennt halluzinierte Fakten und oberflächliche Distraktoren als zentrale Fehlermodi [1][2].
**Entscheidung:** Kein Item erreicht den Nutzer, ohne (a) einen `source_span` zu besitzen, (b) einen Groundedness-Check zu bestehen (LLM-Judge mit *nur* dem zitierten Chunk als Kontext + Embedding-Ähnlichkeit über Schwelle), (c) strukturelle Validatoren zu bestehen, (d) kein Near-Duplicate zu sein.
**Konsequenzen:** (+) Direkt an N6 gekoppelt und im CI messbar; (+) das Zitat ist gleichzeitig Feature (D4: Beleg im Feedback). (−) Zusätzliche LLM-Kosten pro Item (~30 % Aufschlag) — **Mitigation:** Judge läuft auf einem billigen Modell und nur auf Kandidaten, die die deterministischen Gates bestanden haben.

### ADR-009 — BYOK mit Envelope Encryption, Keys nie zurückgebbar

**Status:** Akzeptiert
**Kontext:** Nutzer hinterlegen fremde Provider-Credentials (A3). Diese direkt in der Anwendungs-DB abzulegen — auch verschlüsselt — gilt als Anti-Pattern.
**Entscheidung:** Envelope Encryption: pro Credential ein zufälliger Data Key (AES-256-GCM), verschlüsselt mit einem KEK aus einem externen Key-Management (Cloud-KMS oder HashiCorp Vault / Infisical). In der DB liegen nur Ciphertext, wrapped DEK, Provider, `keyHint`, `lastVerifiedAt`. Es gibt **keinen** API-Pfad, der den Klartext zurückgibt. Entschlüsselung ausschließlich im Modul `ai`, im Speicher, für die Dauer eines Calls.
**Alternativen:** *Nur DB-Verschlüsselung mit App-Secret* — verworfen (ein DB-Dump + ein Config-Leak = alle Keys); *gar kein BYOK* — widerspricht der Produktentscheidung.
**Konsequenzen:** (+) Kompromittierte DB allein gibt keine Keys preis; (+) Key-Rotation ohne Re-Encryption aller Credentials. (−) Abhängigkeit von einem KMS; (−) lokale Entwicklung braucht einen Dev-KEK-Pfad.

### ADR-010 — Modell-Routing nach Task-Klasse

**Status:** Akzeptiert
**Kontext:** N8 (< 0,50 € pro 100-Seiten-Dokument) ist mit einem einzigen Top-Modell für alles nicht erreichbar.
**Entscheidung:** `ModelRouter` bildet Task-Klassen auf Modelle ab:

| Task | Modellklasse | Begründung |
|---|---|---|
| Struktur-/Konzeptextraktion | klein/günstig | Klassifikation, hohes Volumen |
| Embeddings | dediziertes Embedding-Modell | – |
| Item-Generierung | mittel–groß | Qualitätskritisch |
| Groundedness-Judge | klein | Binäres Urteil mit Kontext |
| Freitext-Bewertung | mittel | Nuance nötig, geringes Volumen |
| Fehleranalyse (async) | klein–mittel | Nicht latenzkritisch |

Routing ist Konfiguration, nicht Code. Pro Tenant überschreibbar (BYOK-Nutzer wählt eigene Modelle).
**Konsequenzen:** (+) Kosten steuerbar ohne Codeänderung; (+) lokaler Modus = alle Tasks auf Ollama umbiegen. (−) Qualität variiert je Routing → Eval-Suite muss pro Routing-Profil laufen (§17.4).

### ADR-011 — Ein Backend, generierter TypeScript-Client für beide Frontends

**Status:** Akzeptiert
**Entscheidung:** OpenAPI (springdoc) ist die Single Source of Truth; daraus wird ein typisierter TS-Client generiert, den Web-App und Extension als gemeinsames Package nutzen. Geschäftslogik existiert ausschließlich serverseitig.
**Begründung:** Der teuerste Fehler bei „Web + Extension parallel" ist doppelte Logik, die auseinanderdriftet. Die Extension ist ein dünner Client mit einer Zusatzfähigkeit: DOM-Extraktion der aktuellen Seite.
**Konsequenzen:** (+) API-Breaking-Changes brechen den Build der Clients sofort; (−) Extension-Releases hinken (Store-Review) → API muss versioniert und rückwärtskompatibel sein, mindestens N-1.

### ADR-012 — Datenbank-gestützte Job-Verarbeitung statt externem Broker

**Status:** Akzeptiert
**Kontext:** Ingestion und Generierung sind lang laufend, müssen wiederaufsetzbar sein und Fortschritt melden.
**Entscheidung:** Jobs als Tabelle mit `SELECT ... FOR UPDATE SKIP LOCKED`, Worker-Profil pollt. Idempotenz über `job_key` (Unique). Fortschritt über Modulith-Events + SSE. Kandidat für eine fertige Bibliothek (z. B. JobRunr mit Postgres-Backend) statt Eigenbau — Entscheidung beim Bau von M1 anhand von Retry-/Scheduling-Bedarf.
**Alternativen:** *RabbitMQ/Kafka* — verworfen für v1 (ADR-002); *`@Async` ohne Persistenz* — verworfen: ein Neustart verlöre laufende Imports (N14).
**Konsequenzen:** (+) Job-Status ist transaktional mit den Daten konsistent; (+) kein zusätzlicher Betrieb. (−) Polling-Latenz (~1 s, unkritisch); (−) begrenzt skalierbar (ausreichend bis ca. 10 k Jobs/Tag).

---

## 8. Datenmodell

### 8.1 Kern-Schema (PostgreSQL 17 + pgvector)

```sql
-- ═══════════════ identity ═══════════════
CREATE TABLE users (
  id            UUID PRIMARY KEY,
  email         CITEXT UNIQUE NOT NULL,
  display_name  TEXT,
  locale        TEXT NOT NULL DEFAULT 'de',
  plan          TEXT NOT NULL DEFAULT 'FREE',   -- FREE | PRO | EDU
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);

CREATE TABLE workspaces (               -- Tenant-Grenze; v1: 1 pro User
  id       UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name     TEXT NOT NULL
);

-- ═══════════════ ai ═══════════════
CREATE TABLE ai_credentials (
  id              UUID PRIMARY KEY,
  workspace_id    UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  provider        TEXT NOT NULL,          -- OPENAI | ANTHROPIC | GOOGLE | AZURE | OPENROUTER | OLLAMA
  label           TEXT,
  ciphertext      BYTEA NOT NULL,         -- AES-256-GCM(api_key, DEK)
  wrapped_dek     BYTEA NOT NULL,         -- KMS-verschlüsselter Data Key
  nonce           BYTEA NOT NULL,
  key_hint        TEXT NOT NULL,          -- "…a3f9" — einziger Rückgabewert
  base_url        TEXT,                   -- für Ollama / Self-Hosted / Azure
  region          TEXT,                   -- für N10 (Datenfluss-Anzeige)
  last_verified_at TIMESTAMPTZ,
  status          TEXT NOT NULL DEFAULT 'UNVERIFIED',
  UNIQUE (workspace_id, provider, label)
);

CREATE TABLE llm_usage (                  -- N13, §16
  id            BIGSERIAL PRIMARY KEY,
  workspace_id  UUID NOT NULL,
  task          TEXT NOT NULL,            -- CONCEPT_EXTRACTION | ITEM_GENERATION | JUDGE | …
  provider      TEXT NOT NULL,
  model         TEXT NOT NULL,
  input_tokens  INT NOT NULL,
  output_tokens INT NOT NULL,
  cached_tokens INT NOT NULL DEFAULT 0,
  cost_micros   BIGINT NOT NULL,          -- Mikro-Euro, keine Floats bei Geld
  latency_ms    INT NOT NULL,
  outcome       TEXT NOT NULL,            -- OK | GATE_REJECTED | ERROR
  correlation_id UUID,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON llm_usage (workspace_id, created_at DESC);

-- ═══════════════ content ═══════════════
CREATE TABLE sources (
  id            UUID PRIMARY KEY,
  workspace_id  UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  kind          TEXT NOT NULL,            -- PDF | DOCX | URL | HTML_SNIPPET | TEXT | EPUB
  title         TEXT NOT NULL,
  origin_uri    TEXT,                     -- URL oder S3-Key
  content_hash  BYTEA NOT NULL,           -- SHA-256, Dedup (B7)
  language      TEXT,
  status        TEXT NOT NULL,            -- UPLOADED|EXTRACTING|CHUNKING|INDEXING|READY|PARTIAL|FAILED
  failure_reason TEXT,
  page_count    INT,
  needs_ocr     BOOLEAN NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, content_hash)
);

CREATE TABLE sections (                   -- Dokumentstruktur (B6)
  id          UUID PRIMARY KEY,
  source_id   UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
  parent_id   UUID REFERENCES sections(id) ON DELETE CASCADE,
  ordinal     INT NOT NULL,
  level       SMALLINT NOT NULL,
  title       TEXT NOT NULL,
  excluded    BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE chunks (
  id          UUID PRIMARY KEY,
  source_id   UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
  section_id  UUID REFERENCES sections(id) ON DELETE SET NULL,
  ordinal     INT NOT NULL,
  text        TEXT NOT NULL,
  token_count INT NOT NULL,
  page_from   INT,
  page_to     INT,
  char_from   INT NOT NULL,               -- Offsets im Volltext → exaktes Zitat (P1)
  char_to     INT NOT NULL,
  embedding   VECTOR(1536),               -- Dimension = Modell; siehe §20 O-2
  UNIQUE (source_id, ordinal)
);
CREATE INDEX chunks_embedding_hnsw ON chunks
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX ON chunks (source_id);

-- ═══════════════ knowledge ═══════════════
CREATE TABLE concepts (
  id            UUID PRIMARY KEY,
  workspace_id  UUID NOT NULL,
  source_id     UUID REFERENCES sources(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  summary       TEXT NOT NULL,
  embedding     VECTOR(1536),
  importance    REAL NOT NULL DEFAULT 0.5,   -- 0..1, steuert Generierungsbudget
  item_target   INT  NOT NULL DEFAULT 5,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX concepts_embedding_hnsw ON concepts USING hnsw (embedding vector_cosine_ops);

CREATE TABLE concept_evidence (
  concept_id UUID REFERENCES concepts(id) ON DELETE CASCADE,
  chunk_id   UUID REFERENCES chunks(id)   ON DELETE CASCADE,
  weight     REAL NOT NULL DEFAULT 1.0,
  PRIMARY KEY (concept_id, chunk_id)
);

CREATE TABLE concept_relations (
  from_id UUID REFERENCES concepts(id) ON DELETE CASCADE,
  to_id   UUID REFERENCES concepts(id) ON DELETE CASCADE,
  kind    TEXT NOT NULL,                   -- PREREQUISITE_OF | PART_OF | RELATED_TO | CONTRASTS_WITH
  PRIMARY KEY (from_id, to_id, kind)
);

-- ═══════════════ authoring ═══════════════
CREATE TABLE items (
  id            UUID PRIMARY KEY,
  workspace_id  UUID NOT NULL,
  concept_id    UUID NOT NULL REFERENCES concepts(id) ON DELETE CASCADE,
  parent_item_id UUID REFERENCES items(id),  -- Paraphrase-Variante (E6)
  type          TEXT NOT NULL,               -- §10
  stem          TEXT NOT NULL,
  payload       JSONB NOT NULL,              -- ADR-007
  explanation   TEXT NOT NULL,
  bloom_level   TEXT NOT NULL,               -- REMEMBER|UNDERSTAND|APPLY|ANALYZE|EVALUATE|CREATE
  language      TEXT NOT NULL,
  -- Beleg (P1)
  source_chunk_id UUID NOT NULL REFERENCES chunks(id),
  source_char_from INT NOT NULL,
  source_char_to   INT NOT NULL,
  -- Kalibrierung (§11)
  difficulty    REAL NOT NULL DEFAULT 0.0,   -- Elo-Logit-Skala
  difficulty_n  INT  NOT NULL DEFAULT 0,     -- Anzahl Antworten → K-Faktor
  p_correct     REAL,                        -- empirisch, für Reports
  -- Qualität (ADR-008)
  status        TEXT NOT NULL DEFAULT 'DRAFT', -- DRAFT|PUBLISHED|REJECTED|RETIRED
  quality       JSONB NOT NULL DEFAULT '{}',  -- Gate-Ergebnisse + Scores
  report_count  INT NOT NULL DEFAULT 0,
  embedding     VECTOR(1536),                 -- Duplikaterkennung (C4)
  generated_by  TEXT,                         -- Modell + Prompt-Version
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON items (concept_id, status, difficulty);
CREATE INDEX items_embedding_hnsw ON items USING hnsw (embedding vector_cosine_ops);

-- ═══════════════ assessment ═══════════════
CREATE TABLE sessions (
  id            UUID PRIMARY KEY,
  workspace_id  UUID NOT NULL,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  scope_kind    TEXT NOT NULL,              -- SOURCE | CONCEPT | DUE_REVIEW | MIXED
  scope_id      UUID,
  goal_kind     TEXT NOT NULL,              -- ITEM_COUNT | DURATION
  goal_value    INT NOT NULL,
  started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at      TIMESTAMPTZ,
  summary       JSONB
);

CREATE TABLE attempts (                     -- append-only (N14)
  id           BIGSERIAL PRIMARY KEY,
  session_id   UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  user_id      UUID NOT NULL,
  item_id      UUID NOT NULL REFERENCES items(id),
  concept_id   UUID NOT NULL,
  response     JSONB NOT NULL,
  outcome      TEXT NOT NULL,               -- CORRECT | PARTIAL | INCORRECT | SKIPPED
  score        REAL NOT NULL,               -- 0..1
  elapsed_ms   INT NOT NULL,
  hint_used    BOOLEAN NOT NULL DEFAULT FALSE,
  theta_before REAL NOT NULL,               -- für Reproduzierbarkeit/Debugging
  theta_after  REAL NOT NULL,
  p_expected   REAL NOT NULL,               -- Vorhersage vor der Antwort → Kalibrierungsmessung
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON attempts (user_id, concept_id, created_at DESC);
CREATE INDEX ON attempts (item_id);

-- ═══════════════ adaptivity ═══════════════
CREATE TABLE learner_concept_state (
  user_id     UUID NOT NULL,
  concept_id  UUID NOT NULL,
  theta       REAL NOT NULL DEFAULT 0.0,    -- Fähigkeit (Logit)
  theta_n     INT  NOT NULL DEFAULT 0,      -- Beobachtungen → adaptives K
  mastery     REAL NOT NULL DEFAULT 0.0,    -- 0..1, für die UI
  -- FSRS (ADR-006)
  fsrs_stability   REAL,
  fsrs_difficulty  REAL,
  last_review_at   TIMESTAMPTZ,
  due_at           TIMESTAMPTZ,
  lapses           INT NOT NULL DEFAULT 0,
  reps             INT NOT NULL DEFAULT 0,
  state            TEXT NOT NULL DEFAULT 'NEW', -- NEW|LEARNING|REVIEW|RELEARNING
  PRIMARY KEY (user_id, concept_id)
);
CREATE INDEX ON learner_concept_state (user_id, due_at);

-- ═══════════════ analytics ═══════════════
CREATE TABLE error_events (
  id           BIGSERIAL PRIMARY KEY,
  attempt_id   BIGINT NOT NULL REFERENCES attempts(id) ON DELETE CASCADE,
  user_id      UUID NOT NULL,
  concept_id   UUID NOT NULL,
  category     TEXT NOT NULL,               -- §11.5
  detail       TEXT,
  confidence   REAL NOT NULL,
  detected_by  TEXT NOT NULL,               -- RATIONALE | LLM | HEURISTIC
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE misconceptions (
  id           UUID PRIMARY KEY,
  user_id      UUID NOT NULL,
  concept_id   UUID NOT NULL,
  label        TEXT NOT NULL,
  occurrences  INT NOT NULL DEFAULT 1,
  first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at   TIMESTAMPTZ,
  UNIQUE (user_id, concept_id, label)
);
```

### 8.2 Mandantentrennung (N9)

Zwei Verteidigungslinien:

1. **Anwendungsschicht:** Jedes Repository erhält `workspace_id` aus dem `TenantContext` (aus dem JWT). Ein ArchUnit-Test verbietet Repository-Methoden ohne Tenant-Parameter auf mandantenfähigen Tabellen.
2. **Datenbank:** PostgreSQL Row Level Security auf allen `workspace_id`-Tabellen, `SET LOCAL app.workspace_id` pro Transaktion. Selbst ein vergessener Filter oder eine SQL-Injection liefert dann keine Fremddaten.

Die zweite Linie ist bewusst redundant. Ein einziger Datenleck-Vorfall wäre bei einem Produkt mit Uni-Skripten existenzbedrohend (Persona P4).

### 8.3 Migrationen

Flyway, versionierte SQL-Skripte, keine `hibernate.ddl-auto` außerhalb von Tests. `pgvector`-Extension und HNSW-Indizes explizit in Migrationen — nicht durch `initialize-schema` des Vector Stores, damit Schemaänderungen nachvollziehbar bleiben.

---

## 9. API-Design

### 9.1 Prinzipien

- REST + JSON, `/api/v1`, Ressourcen im Plural, `problem+json` (RFC 9457) für Fehler
- Lang laufende Operationen: `202 Accepted` + Job-Ressource + SSE-Stream für Fortschritt
- Cursor-Pagination (`?cursor=…&limit=…`), keine Offsets
- Idempotenz-Key-Header bei allen erzeugenden POSTs
- Optimistisches Locking über `ETag`/`If-Match` bei editierbaren Ressourcen
- OpenAPI ist Quelle der Wahrheit (ADR-011)

### 9.2 Endpunkte (Auszug)

```
── Auth & Konto ──────────────────────────────────────────────
POST   /api/v1/auth/token                  OAuth2/OIDC Code→Token (PKCE)
POST   /api/v1/auth/refresh
GET    /api/v1/me
DELETE /api/v1/me                          Kontolöschung (A5)

── AI-Credentials (BYOK) ─────────────────────────────────────
GET    /api/v1/ai/providers                verfügbare Provider + Modelle + Region
POST   /api/v1/ai/credentials              { provider, apiKey, baseUrl?, label? } → 201 (ohne Key!)
POST   /api/v1/ai/credentials/{id}/verify  Testcall → status
DELETE /api/v1/ai/credentials/{id}
GET    /api/v1/ai/routing                  aktuelles Task→Modell-Mapping
PUT    /api/v1/ai/routing                  Overrides pro Task
GET    /api/v1/ai/usage?from=&to=          Tokens & Kosten (A4)

── Import ────────────────────────────────────────────────────
POST   /api/v1/sources                     multipart | { url } | { html, url, title }  → 202
GET    /api/v1/sources?cursor=
GET    /api/v1/sources/{id}
GET    /api/v1/sources/{id}/events         text/event-stream: Status + Fortschritt
GET    /api/v1/sources/{id}/sections
PATCH  /api/v1/sources/{id}/sections/{sid} { excluded: true }   (B6)
POST   /api/v1/sources/{id}/ocr            OCR anstoßen (B5)
DELETE /api/v1/sources/{id}

── Wissen & Items ────────────────────────────────────────────
GET    /api/v1/sources/{id}/concepts
PATCH  /api/v1/concepts/{id}               Name/Wichtigkeit korrigieren
POST   /api/v1/concepts/{id}/items:generate { count, types[] }  → 202  (C6)
GET    /api/v1/concepts/{id}/items
POST   /api/v1/items/{id}/report           { reason, comment? }           (C5)

── Lernsession ───────────────────────────────────────────────
POST   /api/v1/sessions                    { scope, goal }  → Session + erstes Item
GET    /api/v1/sessions/{id}/next          nächstes Item (Prefetch/Offline)
POST   /api/v1/sessions/{id}/attempts      { itemId, response, elapsedMs } → Feedback + nächstes Item
POST   /api/v1/sessions/{id}/skip          { itemId, reason }
POST   /api/v1/sessions/{id}/finish        → Zusammenfassung
GET    /api/v1/sessions/{id}

── Fortschritt & Analyse ─────────────────────────────────────
GET    /api/v1/progress/overview
GET    /api/v1/progress/concepts?sourceId=
GET    /api/v1/progress/due                fällige Wiederholungen (FSRS)
GET    /api/v1/progress/misconceptions
GET    /api/v1/export                      vollständiger Datenexport (P3, DSGVO)
```

### 9.3 Die wichtigste Response: `POST /sessions/{id}/attempts`

Dieser Endpunkt trägt das Produkterlebnis. Er liefert Bewertung, Erklärung, Beleg, Lernstand-Update *und* die nächste Frage in einem Roundtrip — deshalb ist N1 (p95 < 400 ms) überhaupt erreichbar.

```json
{
  "attemptId": 918273,
  "outcome": "INCORRECT",
  "score": 0.0,
  "feedback": {
    "correctResponse": { "optionIds": ["c"] },
    "explanation": "3NF verlangt zusätzlich, dass keine transitiven Abhängigkeiten von Nichtschlüsselattributen bestehen.",
    "chosenOptionRationale": "Du hast 2NF gewählt — 2NF eliminiert nur partielle Abhängigkeiten vom Schlüssel.",
    "evidence": {
      "quote": "Eine Relation ist in 3NF, wenn sie in 2NF ist und kein Nichtschlüsselattribut transitiv vom Primärschlüssel abhängt.",
      "sourceId": "…", "chunkId": "…", "page": 84
    }
  },
  "errorAnalysis": { "category": "CONCEPT_CONFUSION", "confidence": 0.82,
                     "note": "Verwechslung 2NF ↔ 3NF (3. Mal in 14 Tagen)" },
  "learnerUpdate": { "conceptId": "…", "thetaBefore": 0.41, "thetaAfter": 0.19,
                     "mastery": 0.46, "nextDueAt": "2026-07-30T09:00:00Z" },
  "next": {
    "itemId": "…", "type": "ORDERING", "stem": "Bringe die Normalformen …",
    "payload": { "elements": [ … ] },
    "meta": { "conceptId": "…", "expectedSuccess": 0.74 }
  }
}
```

`expectedSuccess` ist bewusst Teil der Antwort: Es macht die Adaption transparent (P4) und ist gleichzeitig das Feld, gegen das wir die Kalibrierung messen (§17.5).

---

## 10. Fragetypen-Katalog

### 10.1 Typen (v1)

| Typ | Bloom | Auto-Grading | MVP | Payload-Kern |
|---|---|---|---|---|
| `MC_SINGLE` | Remember–Apply | ✅ deterministisch | ✅ | `options[{id,text,correct,rationale}]` |
| `MC_MULTI` | Understand–Analyze | ✅ (Teilpunkte) | ✅ | wie oben, ≥ 2 `correct` |
| `TRUE_FALSE` | Remember–Understand | ✅ | ✅ | `statement`, `answer`, `rationale` |
| `ORDERING` | Understand–Apply | ✅ (Kendall-Tau) | ✅ | `elements[{id,text}]`, `correctOrder[]` |
| `MATCHING` | Understand | ✅ | ✅ | `left[]`, `right[]`, `pairs[]`, `distractorsRight[]` |
| `CLOZE` | Remember–Apply | ✅ (Normalisierung + Synonyme) | ✅ | `template` mit `{{1}}`, `blanks[{accepted[]}]` |
| `SHORT_ANSWER` | Apply–Evaluate | ⚠️ LLM + Rubric | S | `rubric[{criterion,points}]`, `referenceAnswer` |
| `NUMERIC` | Apply | ✅ (Toleranz, Einheit) | S | `value`, `tolerance`, `unit` |
| `CATEGORIZATION` | Analyze | ✅ | S | `buckets[]`, `elements[{id,bucketId}]` |
| `HOTSPOT` | Understand | ✅ | C | `imageRef`, `regions[]` |
| `CODE_OUTPUT` | Analyze | ✅ | C | `snippet`, `language`, `expected` |

### 10.2 Modellierung in Kotlin (ADR-007)

```kotlin
@JsonTypeInfo(use = NAME, property = "type")
sealed interface ItemPayload {
    fun validate(): List<ValidationError>
}

@JsonTypeName("MC_SINGLE")
data class McSinglePayload(
    val options: List<Option>,
    val shuffle: Boolean = true
) : ItemPayload {
    override fun validate() = buildList {
        if (options.count { it.correct } != 1) add(ExactlyOneCorrectRequired)
        if (options.size !in 3..6)             add(OptionCountOutOfRange)
        if (options.distinctBy { it.text.normalized() }.size != options.size)
                                               add(DuplicateOptions)
        if (options.any { it.text.matchesAllOfTheAbove() }) add(ForbiddenOptionPattern)
        if (options.lengthVarianceRatio() > 2.0) add(LengthCueDetected)  // C3
        if (options.any { it.rationale.isBlank() }) add(MissingRationale) // C8
    }
}

@JsonTypeName("ORDERING")
data class OrderingPayload(
    val elements: List<Element>,
    val correctOrder: List<String>,
    val partialCredit: Boolean = true
) : ItemPayload { /* … */ }

// Grading: exhaustives when — neuer Typ ⟹ Compilerfehler hier
fun grade(payload: ItemPayload, response: Response): Grade = when (payload) {
    is McSinglePayload      -> gradeMcSingle(payload, response)
    is McMultiPayload       -> gradeMcMulti(payload, response)
    is TrueFalsePayload     -> gradeTrueFalse(payload, response)
    is OrderingPayload      -> gradeOrdering(payload, response)     // Kendall-Tau
    is MatchingPayload      -> gradeMatching(payload, response)
    is ClozePayload         -> gradeCloze(payload, response)
    is ShortAnswerPayload   -> gradeShortAnswer(payload, response)  // ⚠ asynchron
    is NumericPayload       -> gradeNumeric(payload, response)
    is CategorizationPayload-> gradeCategorization(payload, response)
}
```

### 10.3 Teilpunkte

Nicht alles ist richtig/falsch — das ist wichtig für ein sinnvolles Elo-Signal:

| Typ | Score |
|---|---|
| `MC_MULTI` | `max(0, (TP − FP) / \|correct\|)` — verhindert „alles ankreuzen" |
| `ORDERING` | Normalisiertes Kendall-Tau, auf [0,1] skaliert |
| `MATCHING` | Anteil korrekter Paare |
| `CLOZE` | Anteil korrekter Lücken |
| `SHORT_ANSWER` | Rubric-Punkte / Maximum |

Für das Elo-Update wird der stetige Score direkt als Ergebnis `r ∈ [0,1]` verwendet — Elo ist dafür ohne Anpassung geeignet.

---

## 11. Adaptive Engine

Der Kern des Produkts. Bewusst deterministisch, ohne LLM (§6.3).

### 11.1 Zwei getrennte Modelle

| | **Fähigkeitsmodell (Elo)** | **Gedächtnismodell (FSRS)** |
|---|---|---|
| Beantwortet | „Wie schwer darf die nächste Frage sein?" | „Wann muss ich das wiedersehen?" |
| Granularität | Learner × Concept | Learner × Concept |
| Update | nach jeder Antwort | nach jeder Antwort |
| Steuert | *Welches Item* | *Ob und wann* das Concept drankommt |

### 11.2 Elo-Update

Erfolgswahrscheinlichkeit (logistisch, Rasch-äquivalent):

```
P(korrekt) = 1 / (1 + exp(-(θ_u − d_i)))
```

Update nach Beobachtung `r ∈ [0,1]`:

```
θ_u ← θ_u + K_u · (r − P)
d_i ← d_i + K_i · (P − r)
```

Unsicherheitsabhängiger K-Faktor (mehr Beobachtungen → stabiler):

```
K(n) = a / (1 + b·n)          Start: a_user = 0.6, b_user = 0.05
                                     a_item = 0.8, b_item = 0.10
```

Neue Items bewegen sich anfangs schnell (a_item hoch), etablierte kaum noch. Das ist genau der Cold-Start-Vorteil, wegen dem wir Elo gewählt haben (ADR-005) [3][4].

**Startwerte:**
- Neues Item: `d = 0` (mittel), außer das generierende Modell hat eine Schwierigkeit geschätzt → dann `d ∈ {−0.8, 0, +0.8}` je nach `bloom_level`.
- Neuer Nutzer: `θ = 0` für alle Concepts; **oder** initialisiert aus dem Mittel seiner θ auf verbundenen Concepts (`PREREQUISITE_OF`) — mildert den Nutzer-Cold-Start spürbar.

**Antwortzeit als Zusatzsignal** (mildert das fehlende Rate-Modell, ADR-005): Eine korrekte Antwort in unter 20 % der Median-Zeit für dieses Item wird leicht abgewertet (`r = 0.9` statt `1.0`) — mögliches Raten oder Wiedererkennen statt Wissen.

### 11.3 Item-Auswahl (Selection Policy)

```
1. Kandidatenpool bilden
   a) Fällige Concepts (FSRS due_at ≤ jetzt)            Gewicht 0.5
   b) Aktuelles Lernziel (Session-Scope)                 Gewicht 0.3
   c) Schwächste Concepts (niedrigste mastery)           Gewicht 0.2
   → nach Prerequisite-Graph filtern: kein Concept, dessen
     Voraussetzungen mastery < 0.4 haben

2. Items je Concept filtern
   − status = PUBLISHED
   − nicht in den letzten 10 Attempts gesehen
   − wenn zuletzt falsch: Paraphrase-Variante bevorzugen (E6)
   − Typ-Rotation erzwingen: nicht 3× derselbe Typ hintereinander (D9)

3. Nach Zielschwierigkeit sortieren
   score(i) = −|P(θ_u, d_i) − P_target| + ε·explore(i)
   P_target = 0.78     (Band 0.70–0.85)
   explore(i) = 1/(1+difficulty_n)   → neue Items werden bevorzugt kalibriert

4. Softmax-Auswahl aus Top-5 (τ = 0.3) statt Argmax
   → verhindert deterministische Sequenzen und ermöglicht Exploration
```

**Warum P_target ≈ 0.78?** Zu leicht = keine Lernwirkung und Langeweile; zu schwer = Frustration und Abbruch. Die Literatur zur „Desirable Difficulty" und zum optimalen Trainingsfehler verortet das Optimum im Bereich von rund 70–85 % Erfolgsquote. Der Wert ist als Konfiguration ausgeführt und ein A/B-Test-Kandidat (§18, M5).

### 11.4 FSRS-Scheduling

Zustand pro Karte: **D**ifficulty, **S**tability, **R**etrievability.

```
R(t) = (1 + FACTOR · t/S)^DECAY            Abrufwahrscheinlichkeit nach t Tagen
I(R_target) = (S/FACTOR) · (R_target^(1/DECAY) − 1)     nächstes Intervall
```

Nach jeder Antwort werden S und D nach den FSRS-Formeln aktualisiert, mit einem Grade aus unserem Score:

| Score | FSRS-Grade |
|---|---|
| `< 0.4` | `AGAIN` (1) |
| `0.4–0.7` | `HARD` (2) |
| `0.7–0.95` | `GOOD` (3) |
| `> 0.95` und schnell | `EASY` (4) |

`R_target` ist nutzerkonfigurierbar (Default 0.90); höher = mehr Wiederholungen, bessere Retention.

**Umsetzung:** Verfügbare JVM-Portierungen prüfen; andernfalls Kernalgorithmus selbst implementieren (Parametervektor + ~200 LOC) und gegen die Referenz-Testvektoren des Open-Source-Projekts verifizieren (§20, O-3).

### 11.5 Fehlertaxonomie (E1)

| Kategorie | Erkennung | Intervention |
|---|---|---|
| `FACTUAL_GAP` | Distraktor mit unbekanntem Fakt gewählt | Belegstelle zeigen, Concept früher wiedervorlegen |
| `TERM_CONFUSION` | Distraktor ist ein verwandter Begriff (`CONTRASTS_WITH`) | Kontrastfrage: „Was unterscheidet A von B?" |
| `CONCEPT_CONFUSION` | Distraktor gehört zu einem Nachbar-Concept | Beide Concepts in einer Zuordnungsfrage gegenüberstellen |
| `PROCEDURAL` | Rechen-/Ablauffehler bei `NUMERIC`/`ORDERING` | Schrittweise Aufgabe, Teilschritte einzeln abfragen |
| `CARELESS` | θ hoch, P hoch, Antwortzeit sehr kurz, sonst korrekt | Kein θ-Abzug, nur Hinweis; nicht als Wissenslücke werten |
| `AMBIGUOUS_ITEM` | Viele starke Lernende scheitern am selben Item | **Item** flaggen, nicht den Nutzer → Review-Queue |

**Entscheidend:** Die ersten drei Kategorien werden **ohne LLM-Call zur Laufzeit** erkannt, weil jeder Distraktor bereits bei der Generierung eine `rationale` und optional eine `misconceptionTag` erhalten hat (C8). Das ist der Grund, warum „Echtzeit-Fehlerkorrektur" die Latenzvorgabe N1 einhalten kann.

`AMBIGUOUS_ITEM` ist die wichtigste Kategorie für die Produktqualität: Sie dreht die Perspektive um und nutzt die Antwortdaten zur Fehlererkennung *an der Item-Bank*. Konkret: Wenn Lernende mit `θ > d + 1.5` an einem Item scheitern (Rate deutlich über Erwartung), ist mit hoher Wahrscheinlichkeit das Item defekt, nicht der Lernende.

---

## 12. LLM-Pipeline & Qualitätssicherung

### 12.1 Pipeline-Stufen

```
Chunk-Batch
   │
   ├─▶ [1] CONCEPT_EXTRACTION      klein/günstig · Structured Output
   │        → Concepts + Belegstellen + Wichtigkeit
   │        Dedup gegen bestehende Concepts (Embedding > 0.90 → mergen)
   │
   ├─▶ [2] ITEM_GENERATION         mittel/groß · Structured Output · pro Concept
   │        Input: Concept + Top-k Belegchunks (kein Volltext!)
   │        Output: N Items mit Typ-Mix, jeweils
   │                stem, payload, explanation, sourceSpan,
   │                rationale je Option, misconceptionTag je Distraktor,
   │                geschätzte Schwierigkeit, Bloom-Level
   │
   ├─▶ [3] GATE: strukturell       kein LLM · Millisekunden
   │        §10.2 validate() · Reject → Ende
   │
   ├─▶ [4] GATE: Groundedness      klein · nur zitierter Chunk als Kontext
   │        „Folgt Frage+Antwort ausschließlich aus diesem Text?" → ja/nein/teilweise
   │        + Embedding-Similarity(Item, Chunk) > 0.75
   │
   ├─▶ [5] GATE: Duplikat          kein LLM · Vektorsuche
   │        Cosine > 0.93 gegen Items desselben Concepts → Reject
   │
   └─▶ [6] PUBLISH                 status=PUBLISHED, Event ItemPublished
```

### 12.2 Prompt-Design-Regeln

1. **Nie das ganze Dokument in den Prompt.** Immer nur Concept + zugehörige Chunks. Das begrenzt Kosten (N8), verbessert Groundedness und macht Ergebnisse reproduzierbar.
2. **Structured Output nutzen, nicht Freitext parsen.** Spring AI erzeugt JSON Schema aus der Kotlin-Data-Class und kann es bei unterstützenden Providern nativ als Constraint durchreichen (`useProviderStructuredOutput`), statt es nur als Prompt-Anweisung zu senden [5][8]. Bei Providern ohne native Unterstützung greift die Schema-im-Prompt-Variante plus Retry.
3. **Distraktoren nach expliziten Strategien erzeugen, nicht „erfinde 3 falsche Antworten".** Die Forschung zeigt deutlich bessere Distraktorqualität, wenn Experten-Heuristiken explizit in den Prompt gegeben werden [1][2]. Unsere Strategieliste: *verwandter Begriff aus dem Nachbarkapitel*, *korrekte Aussage zur falschen Frage*, *typischer Umkehrfehler*, *Teilwahrheit ohne notwendige Bedingung*, *plausible Zahl mit falscher Einheit*.
4. **Jeder Distraktor bekommt eine `misconceptionTag`.** Das ist die Brücke zur Fehleranalyse ohne Laufzeit-LLM (§11.5).
5. **Prompts sind versioniert** (`prompts/item_generation/v3.md`), und die Version steht in `items.generated_by`. Ohne das ist Qualitätsregression nicht auffindbar.
6. **Keine Nutzerinhalte in Systemprompts.** Nutzerdokumente sind Daten, nicht Instruktionen — Trennung von Instruction und Content als Prompt-Injection-Schutz (§15).

### 12.3 Robustheit

| Fehlermodus | Behandlung |
|---|---|
| Ungültiges JSON | Retry mit Fehlermeldung im Prompt (max. 2), dann Item verwerfen |
| Provider-Timeout/5xx | Exponentielles Backoff, dann Fallback-Modell laut Routing |
| Rate Limit | Token-Bucket pro Credential, Job wird zurückgestellt statt zu scheitern |
| Provider komplett aus | Job in `WAITING_PROVIDER`; Lernen aus der Bank läuft weiter (N5) |
| Lokales Modell liefert Müll | Gates greifen; bei Ausbeute < 30 % Nutzerhinweis „Modell ungeeignet" |
| Kostenlimit erreicht | Job pausiert, klare Meldung, kein stiller Abbruch |

### 12.4 Qualitätsmetriken (kontinuierlich)

| Metrik | Ziel | Erhebung |
|---|---|---|
| Gate-Durchlassquote | 60–85 % | pro Prompt-Version, pro Modell |
| Faktische Fehlerrate | ≤ 5 % (N6) | Gold-Set, manuell annotiert |
| Nutzer-Report-Rate | < 3 % (N7) | Produktion |
| Distraktor-Attraktivität | jeder Distraktor > 5 % Wahlanteil | Item-Analyse |
| Trennschärfe (Point-Biserial) | > 0.2 | Item-Analyse |
| Anteil trivialer Items | < 10 % | `p_correct > 0.95` bei n ≥ 30 |
| Kalibrierungsfehler | Brier-Score < 0.18 | `p_expected` vs. `outcome` |

Die letzten vier sind klassische Item-Analyse. Items, die durchfallen, wandern automatisch nach `RETIRED` — die Bank verbessert sich mit der Nutzung von selbst.

---

## 13. AI-Account / BYOK

### 13.1 Betriebsmodi

| Modus | Zielgruppe | Datenfluss | Kosten |
|---|---|---|---|
| **Plattform-Key (Free/Pro)** | Einstieg, P1 | Unsere Provider-Verträge, EU-Region | wir zahlen → Limit/Abo |
| **BYOK** | Power-User, P2 | Nutzer-Account beim Provider | Nutzer zahlt direkt |
| **Lokal (Ollama)** | Datenschutz-Sensible, P4 | verlässt das Gerät/Netz nicht | keine |
| **Hybrid** | Fortgeschritten | lokal für Extraktion/Embedding, Cloud nur für Generierung | reduziert |

Der Hybrid-Modus ist die interessanteste Variante: Embeddings und Konzeptextraktion machen den Großteil des Token-Volumens aus und sind für kleine lokale Modelle gut geeignet. Nur die Fragegenerierung braucht Qualität. Das senkt Cloud-Kosten deutlich, ohne die Fragenqualität anzutasten.

### 13.2 Credential-Sicherheit (ADR-009)

```
Speichern:
  DEK ← random(32B)
  ciphertext ← AES-256-GCM(apiKey, DEK, nonce)
  wrappedDek ← KMS.encrypt(DEK)              # KEK verlässt das KMS nie
  DB ← {ciphertext, wrappedDek, nonce, keyHint="…"+last4, provider, region}

Nutzen (nur in ai/internal):
  DEK ← KMS.decrypt(wrappedDek)              # gecacht, TTL 5 min
  apiKey ← AES-256-GCM-decrypt(...)          # CharArray, nach Gebrauch überschrieben
  → Provider-Call
```

Regeln, die im Code durchgesetzt werden:
- Kein API-Endpunkt gibt jemals Klartext zurück (A3).
- Kein Logging-Appender darf das Credential-Objekt serialisieren → eigener Typ `SecretValue` mit `toString() = "***"`.
- Keine Weitergabe an Clients, auch nicht für „direkte Browser-Calls".
- Rotation und Widerruf sind Einzeloperationen ohne Migration.

### 13.3 „Login mit AI-Account"

Wo Provider OAuth anbieten (z. B. OpenRouter mit PKCE), ist der Flow ein normaler OAuth-Connect statt Copy-Paste eines Keys — deutlich bessere UX und widerrufbar auf Provider-Seite. Wo nicht, bleibt es beim manuellen Key mit sofortiger Verifikation (`/verify`-Testcall). Das Modul `ai` modelliert beides einheitlich als `AiCredential` mit `authKind = API_KEY | OAUTH`, sodass die restliche Anwendung den Unterschied nicht kennt.

> **Zu prüfen vor Implementierung (§20, O-4):** Welche Provider bieten aktuell OAuth für Drittanbieter-Apps, und erlauben ihre Nutzungsbedingungen den Einsatz von Endnutzer-Keys durch eine Drittanwendung? Das ist eine rechtliche, keine technische Frage, und sie unterscheidet sich pro Provider.

---

## 14. Chrome-Extension

### 14.1 Aufbau (MV3)

```
extension/
├── manifest.json          MV3
├── background/            Service Worker: Auth, API-Calls, Kontextmenü
├── sidepanel/             React-App (teilt Komponenten mit der Web-App)
├── content/               DOM-Extraktion (Readability), Selektion
└── shared/                generierter API-Client (ADR-011)
```

**Permissions bewusst minimal (F4):**

```json
{
  "manifest_version": 3,
  "permissions": ["activeTab", "sidePanel", "storage", "identity", "contextMenus"],
  "host_permissions": [],
  "optional_host_permissions": ["https://*/*"],
  "background": { "service_worker": "background/index.js", "type": "module" }
}
```

`activeTab` statt `<all_urls>`: Zugriff nur nach expliziter Nutzeraktion. Das ist der Unterschied zwischen einer Store-Freigabe in Tagen und einer Ablehnung mit Rückfragen — und ein Vertrauenssignal in der Store-Beschreibung.

### 14.2 Authentifizierung

`chrome.identity.launchWebAuthFlow` mit Authorization Code + PKCE gegen unseren IdP. Der Redirect landet auf der `chrome-extension://<id>.chromiumapp.org/`-URL, aus der der Code extrahiert und gegen Tokens getauscht wird [9][10].

**Token-Handling:**
- Access Token (15 min): nur im Speicher des Service Workers.
- Refresh Token: `chrome.storage.session` (nicht `local` — überlebt den Browser-Neustart nicht, das ist hier ein Feature).
- Da MV3-Service-Worker jederzeit beendet werden, muss der Refresh-Pfad idempotent und beim ersten API-Call rekonstruierbar sein.
- Auth-Flow **immer** durch eine sichtbare Nutzeraktion ausgelöst, nie im Hintergrund — sonst erscheinen kontextlose Zustimmungsdialoge, was Chrome ausdrücklich als schlechte Praxis benennt [10].

### 14.3 Content-Extraktion

Der Content Script extrahiert das Artikel-DOM lokal (Readability-Ansatz) und sendet **den Text, nicht die URL** (B3). Vorteile: funktioniert hinter Logins und Paywalls, die der Nutzer legitim geöffnet hat, spart einen serverseitigen Fetch, und der Server sieht nie mehr, als der Nutzer bewusst geschickt hat.

Serverseitig für den URL-Import (B2) nutzen wir dieselbe Logik auf der JVM: **Readability4J** (Kotlin-Port von Mozilla Readability, basiert auf jsoup) liefert dieselben Ergebnisse wie Firefox' Reader View [11]. Damit sind Client- und Serverpfad konsistent.

### 14.4 Nutzungsfluss (Persona P2)

```
Nutzer liest AWS-Doku
  → Klick aufs Extension-Icon → Side Panel öffnet
  → „Diese Seite lernen" → Content Script extrahiert → POST /sources (HTML_SNIPPET)
  → nach ~15 s erste Frage im Panel, Seite bleibt sichtbar daneben
  → beantworten, Feedback mit Zitat, weiter
ODER
  Text markieren → Rechtsklick → „Frage erzeugen" → Panel zeigt Frage zur Markierung
```

---

## 15. Security, Datenschutz, Compliance

### 15.1 Bedrohungsmodell (Auszug, STRIDE-orientiert)

| Bedrohung | Vektor | Gegenmaßnahme |
|---|---|---|
| Fremde Daten lesen | IDOR, fehlender Tenant-Filter | Tenant-Kontext + RLS (§8.2) + Test, der Cross-Tenant-Zugriff erzwingt |
| API-Key-Diebstahl | DB-Dump, Log-Leak | Envelope Encryption (ADR-009), `SecretValue`, Log-Scrubbing |
| Prompt Injection über Dokumente | „Ignoriere Anweisungen …" im PDF | Dokumentinhalt nur in User-Role, nie System-Role; Output-Schema erzwungen; Gates prüfen Groundedness, nicht Instruktionsbefolgung |
| SSRF beim URL-Import | `http://169.254.169.254/…` | Allowlist auf http/https, DNS-Auflösung prüfen, private IP-Bereiche blockieren, Redirect-Limit, Timeout, Größenlimit |
| Malicious Upload | ZIP-Bomb, XXE, Riesen-PDF | Größen-/Seitenlimit, XXE in Parsern deaktiviert, Extraktion mit Zeit-/Speicherbudget, idealerweise separater Prozess |
| Kosten-DoS | Massen-Upload zur Token-Verbrennung | Quota pro Plan, Rate Limit pro Workspace, Kosten-Circuit-Breaker |
| XSS im Frontend | Item-Text aus LLM | Kein `dangerouslySetInnerHTML`; Markdown mit strikter Allowlist gerendert |
| Token-Diebstahl aus Extension | XSS auf besuchter Seite | Tokens nur im Service Worker, nie im Content Script / `window` |

### 15.2 DSGVO-Konkretes

- **Rechtsgrundlage:** Vertrag (Art. 6 Abs. 1 lit. b) für die Kernfunktion; Einwilligung für optionale Telemetrie.
- **Auftragsverarbeitung:** AVV mit LLM-Provider erforderlich; Opt-out aus Training verpflichtend prüfen und dokumentieren; lokaler Modus als Alternative für Fälle, in denen das nicht geht.
- **Datenminimierung:** Rohdateien nach erfolgreicher Extraktion optional löschbar (Chunks reichen für den Betrieb).
- **Löschung (A5):** Kaskade über alle Tabellen; S3-Objekte inklusive; Bestätigung mit Zeitstempel im Audit-Log.
- **Auskunft/Portabilität:** `/api/v1/export` liefert JSON mit allen Nutzerdaten inkl. Attempts.
- **Transparenz (N10):** Vor dem ersten Call zeigt die UI, welcher Provider in welcher Region welche Daten erhält. Das ist zugleich ein Vertrauens-Feature.
- **Auftragsverarbeiter-Liste** und Datenflussdiagramm sind Teil der Dokumentation, nicht nachträglich.

---

## 16. Observability & Kostenkontrolle

### 16.1 Telemetrie

- **Tracing:** OpenTelemetry; ein Trace pro Import-Job und pro Attempt-Request. Spring Modulith kann Modulinteraktionen als Spans exportieren, was die Modulgrenzen zur Laufzeit sichtbar macht.
- **Metriken (Micrometer):**
  - `llm.tokens{task,model,provider,direction}`, `llm.cost_micros`, `llm.latency`
  - `item.gate.result{gate,outcome}` — die wichtigste Qualitätsmetrik
  - `session.attempt.latency` (N1), `ingest.duration{stage}` (N2/N3)
  - `adaptivity.calibration.brier`
- **Fachliches Log** für jeden LLM-Call: `correlation_id`, Prompt-Version, Modell, Tokens, Gate-Ergebnis (N13).

### 16.2 Kostensteuerung (N8)

| Hebel | Wirkung |
|---|---|
| Modell-Routing nach Task (ADR-010) | größter Einzelhebel |
| Prompt Caching (systemseitige Anteile stabil halten) | wiederholte Präfixe billiger |
| Nur Chunks statt Volltext in den Prompt | linear statt quadratisch mit der Dokumentgröße |
| Batching bei Embeddings | weniger Overhead |
| Deduplizierung identischer Uploads (B7) | vermeidet Doppelarbeit |
| Generierungsbudget nach `concept.importance` | wichtige Konzepte bekommen mehr Items |
| Inkrementelle Generierung on demand (C6) | nicht alle Items sofort erzeugen |
| Harte Quota + Circuit Breaker | begrenzt Schadensfälle |

**Kostenmodell als Formel** (in der Anwendung implementiert, damit Preisänderungen Konfiguration bleiben):

```
Kosten(Dokument) ≈ Tokens_extraktion · P_klein
                 + Tokens_embedding  · P_embed
                 + n_concepts · items_pro_concept · Tokens_gen · P_mittel
                 + n_kandidaten · Tokens_judge · P_klein
```

Diese Formel wird pro Import *vorab geschätzt* und dem Nutzer angezeigt, bevor der Job startet. Kostenüberraschungen sind bei BYOK der schnellste Weg, Vertrauen zu verlieren.

---

## 17. Teststrategie

### 17.1 Pyramide

| Ebene | Umfang | Werkzeuge |
|---|---|---|
| Unit | Elo/FSRS-Mathematik, Grading, Validatoren, Chunking | Kotest/JUnit 5 |
| Modul | Ein Modul isoliert mit Modulith-Test-Slices | `@ApplicationModuleTest` |
| Integration | Repositories, Vektorsuche, Migrationen | Testcontainers (`pgvector`-Image) |
| Kontrakt | LLM-Provider-Antworten | WireMock mit aufgezeichneten Fixtures |
| E2E | Import → Frage → Antwort → Adaption | Testcontainers + Playwright |
| Architektur | Modulgrenzen (N11) | `ApplicationModules.verify()` + ArchUnit |
| Last | 200 parallele Sessions (N4) | k6/Gatling |
| Sicherheit | Cross-Tenant, SSRF, Injection | eigene Suite, im CI verpflichtend |

### 17.2 Was besonders sorgfältig getestet wird

**Cross-Tenant-Isolation (N9).** Ein parametrisierter Test iteriert über *alle* Repository-Methoden und prüft, dass Zugriff mit fremdem Tenant-Kontext leer zurückkommt oder wirft. Neue Repository-Methode ohne Tenant-Behandlung ⟹ roter Build.

**Determinismus der Adaption.** Elo- und FSRS-Updates sind reine Funktionen: gleiche Eingabe ⟹ gleiche Ausgabe, testbar mit Property-Based Tests (z. B. „θ steigt nie nach einer falschen Antwort", „Stability ist monoton in erfolgreichen Reviews").

### 17.3 LLM-Tests ohne LLM-Aufrufe

Kein CI-Lauf ruft echte Provider. Der `LlmGateway` wird gefakt; Fixtures stammen aus aufgezeichneten echten Antworten (inklusive der kaputten — abgeschnittenes JSON, falsches Schema, leere Optionen). Ein Nightly-Job läuft gegen echte Provider und meldet Abweichungen.

### 17.4 Eval-Harness für Fragenqualität (das wichtigste Testartefakt)

Ein normaler Testlauf sagt nichts über Fragenqualität. Deshalb ein eigenes Verfahren:

```
Gold-Set: 10 Dokumente (verschiedene Fächer/Sprachen/Formate)
          + 200 manuell annotierte Referenz-Items

Lauf (pro Prompt-Version × Routing-Profil):
  1. Vollständige Generierung
  2. Automatische Metriken: Gate-Durchlassquote, Duplikatrate, Typverteilung,
     Bloom-Verteilung, Groundedness-Score
  3. LLM-as-Judge auf einem *anderen* Modell als dem generierenden
  4. Stichprobe von 50 Items zur manuellen Annotation
  5. Report + Vergleich zur Vorversion

Gate im CI: faktische Fehlerrate ≤ 5 % (N6), sonst kein Merge der Prompt-Änderung.
```

Prompt-Änderungen werden damit wie Codeänderungen behandelt: versioniert, getestet, vergleichbar. Ohne das schleicht Qualität unbemerkt weg.

### 17.5 Simulation der Adaptivität

Ein Offline-Simulator erzeugt synthetische Lernende mit bekanntem wahren θ und einer Vergessenskurve und lässt sie N Sessions durchlaufen. Geprüft wird:

- Konvergiert das geschätzte θ gegen das wahre θ? Wie schnell?
- Konvergiert die geschätzte Item-Schwierigkeit gegen die wahre?
- Liegt die tatsächliche Erfolgsquote im Zielband 0.70–0.85?
- Ist der Brier-Score von `p_expected` < 0.18?
- Wie verhält sich das System bei einem Nutzer, der plötzlich viel besser wird?

Das erlaubt es, K-Faktoren und `P_target` zu tunen, **bevor** echte Nutzer betroffen sind — und ist der einzige realistische Weg, Adaptionslogik zu validieren.

---

## 18. Delivery-Plan

### M0 — Walking Skeleton (Woche 1–2)

Gradle-Multiprojekt, Spring Boot + Modulith mit leeren Modulen, Postgres+pgvector via Docker Compose, Flyway, OIDC-Login, `/health`, CI mit `ApplicationModules.verify()`, Testcontainers-Setup.
**Definition of Done:** Ein Text wird hochgeladen, gechunkt, embedded, und `GET /sources/{id}` liefert die Chunks. Deployed, erreichbar, mit Trace.

### M1 — Ingestion (Woche 3–5)

Job-Infrastruktur (ADR-012), PDF via Tika/PDFBox, URL via Readability4J, Struktur- und Konzeptextraktion, SSE-Fortschritt, `ai`-Modul mit `LlmGateway` + Routing + BYOK-Speicherung.
**DoD:** 100-Seiten-PDF → Konzeptliste mit Belegstellen, < 3 min, Kosten gemessen und angezeigt.

### M2 — Generierung + Gates (Woche 6–8)

Item-Generierung für 5 Typen, alle vier Gates, Item-Bank, Eval-Harness mit Gold-Set (§17.4).
**DoD:** Faktische Fehlerrate am Gold-Set ≤ 5 %; Gate-Metriken im Dashboard.

### M3 — Adaptive Loop (Woche 9–11)

Session, Grading aller MVP-Typen, Elo, FSRS, Selection Policy, Feedback mit Beleg, Fehlerklassifikation aus Rationale, Simulator (§17.5).
**DoD:** `POST /attempts` p95 < 400 ms; Simulator zeigt θ-Konvergenz und Zielband-Einhaltung.

### M4 — Clients (Woche 12–15)

Web-App (Bibliothek, Import, Session, Fortschritt), Extension (Side Panel, Auth, Extraktion, Kontextmenü), generierter API-Client, Barrierefreiheit der Session-Ansicht.
**DoD:** Beide Clients laufen gegen dieselbe API; Extension besteht ein internes Store-Policy-Review.

### M5 — Härtung & Beta (Woche 16–19)

Sicherheitssuite, RLS, Quota und Kosten-Circuit-Breaker, Lasttest (N4), Backup/Restore-Probe, Onboarding, Fehlerberichte, A/B-Test-Rahmen für `P_target`.
**DoD:** Alle NFRs aus §4.3 gemessen und erfüllt; 20 Beta-Nutzer aktiv.

### M6 — v1.0 (Woche 20+)

Restliche Fragetypen, Freitext mit Rubric, Wochenreport, Dozenten-Review-Workflow, Export (QTI/Anki), Mehrsprachigkeit.

**Kritischer Pfad:** M2 ist der Meilenstein mit dem höchsten Risiko und dem größten Lerneffekt. Wenn die Fragenqualität dort nicht stimmt, ist alles danach wertlos. Deshalb steht der Eval-Harness in M2 und nicht später — und deshalb würde ich empfehlen, M2 zeitlich großzügiger zu planen als M3 und M4.

---

## 19. Risiken

| # | Risiko | E | A | Frühwarnsignal | Gegenmaßnahme |
|---|---|---|---|---|---|
| R1 | **Fragenqualität ungenügend** | hoch | kritisch | Report-Rate > 5 %, Gold-Set-Fehler > 5 % | Gates (§12), Eval-Harness ab M2, Fallback auf stärkeres Modell, Item-Review |
| R2 | **LLM-Kosten unrentabel** | mittel | hoch | Kosten/Dokument > 1 € | Routing, Caching, BYOK-Push, harte Quota |
| R3 | **Adaption wirkt willkürlich** | mittel | hoch | Nutzer melden „zu leicht/zu schwer" | Simulator, K-Tuning, `expectedSuccess` sichtbar machen, manueller Schwierigkeitsregler |
| R4 | **PDF-Extraktion scheitert an echten Skripten** | hoch | mittel | Anteil `PARTIAL`/`FAILED` > 15 % | OCR-Pfad, Layout-Parser als optionaler Schritt, Nutzer kann Text manuell korrigieren |
| R5 | **Chrome-Store-Ablehnung** | mittel | hoch | Review-Rückfragen | Minimale Permissions (F4), klare Datenschutzerklärung, kein Remote-Code |
| R6 | **Lokale Modelle zu schwach (A-4)** | hoch | mittel | Gate-Durchlassquote < 30 % bei Ollama | Erwartungsmanagement in der UI, Hybrid empfehlen, Mindestmodellgröße dokumentieren |
| R7 | **Rechtliche BYOK-Fragen** | mittel | mittel | Provider-ToS untersagt Drittanbieter-Keys | Pro Provider prüfen (§20 O-4), sonst nur Plattform-Key oder OAuth |
| R8 | **Postgres wird zum Flaschenhals** | niedrig | mittel | p95-Vektorsuche > 100 ms | Read-Replicas, Partitionierung von `attempts`, später externe Vektor-DB (ADR-002 Revisit) |
| R9 | **Scope-Explosion durch zwei Clients** | hoch | mittel | Extension-Features driften ab | ADR-011 strikt: Extension ist dünner Client, Logik nur serverseitig |
| R10 | **Urheberrecht bei geteilten Items** | niedrig | hoch | Feature-Wunsch „Item-Bank teilen" | v1: kein Teilen fremder Inhalte; später nur mit Rechteklärung |

*E = Eintrittswahrscheinlichkeit, A = Auswirkung*

---

## 20. Offene Fragen

| # | Frage | Warum wichtig | Nächster Schritt |
|---|---|---|---|
| O-1 | Zielsprache(n) der Inhalte v1? | Prompts, Stoppwörter, Embedding-Modellwahl, Cloze-Normalisierung | Entscheiden: nur de/en in v1? |
| O-2 | Welches Embedding-Modell (⟹ Vektor-Dimension)? | `VECTOR(n)` ist in Migrationen fixiert; Wechsel = Re-Embedding aller Chunks | Benchmark auf deutschem Fachtext; Dimension bewusst wählen |
| O-3 | FSRS: JVM-Port nutzen oder selbst implementieren? | Lizenz, Wartung, Parameterversion (FSRS-5 vs. 6) | Verfügbare Ports prüfen, sonst Kern portieren + Referenztests |
| O-4 | Welche Provider erlauben Endnutzer-Keys in Drittanwendungen? | Rechtliche Grundlage für BYOK (R7) | ToS je Provider prüfen, Ergebnis dokumentieren |
| O-5 | Hosting & Region? | N10, Kosten, DSGVO-Argumentation gegenüber P4 | EU-Anbieter wählen (z. B. Hetzner/Scaleway), früh festlegen |
| O-6 | Preismodell? | Beeinflusst Quota-Design und Plattform-Key-Budget | Free-Limits definieren, bevor M5 gebaut wird |
| O-7 | Umgang mit Bildern/Formeln/Tabellen im PDF? | Ganze Fächer (Mathe, Medizin) hängen daran | v1: Text-only, Formeln als LaTeX wenn extrahierbar; VLM-Pfad in v2 |
| O-8 | Ein Workspace pro Nutzer oder Teams ab v1? | Datenmodell ist vorbereitet, UI nicht | v1: einer pro Nutzer; Schema bleibt teamfähig |

---

## 21. Quellen

Recherchegrundlage für die Entscheidungen in §7, §11 und §12:

[1] [Orchestrating LLM Agents for Scientific Research: A Pilot Study of MCQ Generation and Evaluation](https://arxiv.org/pdf/2602.18891) — benennt Fehlermodi LLM-generierter Fragen (halluzinierte Fakten, oberflächliche Distraktoren); Grundlage für ADR-008
[2] [Automatic Distractor Generation in Multiple-Choice Questions Using LLMs with Expert-Informed Distractor Strategies](https://library.apsce.net/index.php/ICCE/article/view/5928) — explizite Strategien verbessern Distraktorqualität; Grundlage für §12.2 Regel 3
[3] [Applications of the Elo rating system in adaptive educational systems](https://www.sciencedirect.com/science/article/abs/pii/S036013151630080X) — Elo für adaptive Item-Sequenzierung
[4] [Measuring task difficulty for online learning environments — the Elo rating algorithm approach](https://www.researchgate.net/publication/343295202) — Korrelationswerte bei kleinen Stichproben (r≈0.70 bei n=5); Grundlage für ADR-005
[5] [Spring AI Reference — Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) — JSON-Schema-Generierung, Kotlin-Optionalität; Grundlage für ADR-003/ADR-004
[6] [FSRS vs SM-2: Why the Algorithm Behind Your Flashcard App Matters](https://www.neurako.com/blog/fsrs-vs-sm2-spaced-repetition-algorithms-compared) — DSR-Modell vs. Ease-Faktor
[7] [What Is FSRS? Free Spaced Repetition Scheduler Explained](https://www.deckbase.co/resources/fsrs-guide) — FSRS-6, Anki-Default; Grundlage für ADR-006
[8] [Spring AI Reference — Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) — `entity()`, `useProviderStructuredOutput()`
[9] [Spring AI Reference — Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
[10] [chrome.identity API Reference](https://developer.chrome.com/docs/extensions/reference/api/identity) — `launchWebAuthFlow`, Token-Caching, UX-Regeln; Grundlage für §14.2
[11] [Readability4J — Kotlin-Port von Mozilla Readability](https://github.com/dankito/Readability4J) — serverseitige Artikelextraktion auf der JVM
[12] [Spring Modulith 2.0: Enforcing Module Boundaries Before Microservices](https://www.javacodegeeks.com/2026/07/spring-modulith-2-0-enforcing-module-boundaries-before-microservices.html) — `@ApplicationModule`, Event-Externalisierung; Grundlage für ADR-001
[13] [Building Modular Monoliths With Kotlin and Spring](https://blog.jetbrains.com/kotlin/2026/02/building-modular-monoliths-with-kotlin-and-spring/) — Kotlin + Modulith in der Praxis
[14] [Spring AI and PgVectorStore Configuration](https://howtodoinjava.com/spring-ai/spring-ai-pgvectorstore-example/) — HNSW, Distanzmaße, Dimensionen; Grundlage für ADR-002
[15] [Distractor Generation in Multiple-Choice Tasks: A Survey of Methods, Datasets, and Evaluation](https://arxiv.org/pdf/2402.01512) — Überblick Bewertungsmethoden für Distraktorqualität
[16] [Building a Secure AI LLM SaaS BYOK](https://andyprimawan.com/building-a-secure-ai-llm-saas-byok-using-infisical-secret-management/) — BYOK-Speicherung als Anti-Pattern in der App-DB; Grundlage für ADR-009

---

## Nächster Schritt

Vorschlag für den Einstieg in die Implementierung: **M0 — Walking Skeleton**. Konkret das Gradle-Multiprojekt mit Spring Boot 3.5 + Modulith, den zehn leeren Modulen aus §6.3, `ApplicationModules.verify()` im Build, Docker Compose mit Postgres+pgvector, Flyway-Baseline nach §8.1 und einem ersten End-to-End-Pfad `POST /sources` (Text) → Chunks → Embeddings.

Vorher zu klären: **O-1** (Sprachen) und **O-2** (Embedding-Modell/Dimension), weil beide direkt in die erste Migration einfließen.
