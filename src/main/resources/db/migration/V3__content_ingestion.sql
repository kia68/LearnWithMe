-- Epic B — Dokumenten-Import & Ingestion (§8.1, ADR-012)
CREATE EXTENSION IF NOT EXISTS vector;

-- ═══════════════ platform (Job-Queue, ADR-012) ═══════════════
-- DB-gestützte Queue statt externem Broker: `SELECT ... FOR UPDATE SKIP LOCKED`
-- lässt mehrere Worker-Instanzen sicher um Jobs konkurrieren. `job_key` macht
-- das Enqueuen idempotent (z.B. ein Ingest-Job pro Source).
-- `payload` bewusst TEXT statt JSONB: Hibernates jsonb-Schreibpfad braucht einen
-- expliziten JdbcType und ist ohne laufende Postgres-Instanz hier nicht verifizierbar
-- (gleiche Abwägung wie CITEXT in V1). Payload ist ein Jackson-serialisiertes JSON-Objekt.
CREATE TABLE jobs (
  id           UUID PRIMARY KEY,
  job_key      TEXT NOT NULL UNIQUE,
  type         TEXT NOT NULL,               -- INGEST | OCR
  payload      TEXT NOT NULL,
  status       TEXT NOT NULL DEFAULT 'PENDING', -- PENDING|RUNNING|DONE|FAILED
  attempts     INT NOT NULL DEFAULT 0,
  last_error   TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at   TIMESTAMPTZ,
  finished_at  TIMESTAMPTZ
);
CREATE INDEX jobs_pending_idx ON jobs (created_at) WHERE status = 'PENDING';

-- ═══════════════ content ═══════════════
CREATE TABLE sources (
  id             UUID PRIMARY KEY,
  workspace_id   UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  kind           TEXT NOT NULL,             -- PDF|DOCX|URL|HTML_SNIPPET|TEXT|EPUB
  title          TEXT NOT NULL,
  origin_uri     TEXT,                      -- URL oder S3-Key der Rohdatei
  content_hash   BYTEA NOT NULL,            -- SHA-256, Dedup (B7)
  language       TEXT,
  status         TEXT NOT NULL,             -- UPLOADED|EXTRACTING|CHUNKING|INDEXING|READY|PARTIAL|FAILED
  failure_reason TEXT,
  page_count     INT,
  needs_ocr      BOOLEAN NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, content_hash)
);

CREATE TABLE sections (                     -- Dokumentstruktur (B6)
  id         UUID PRIMARY KEY,
  source_id  UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
  parent_id  UUID REFERENCES sections(id) ON DELETE CASCADE,
  ordinal    INT NOT NULL,
  level      INT NOT NULL,             -- bewusst INT statt SMALLINT: einfache 1:1-Abbildung
                                        -- auf Kotlin Int, Wertebereich (h1..h6) macht den
                                        -- SMALLINT-Platzvorteil irrelevant
  title      TEXT NOT NULL,
  excluded   BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ON sections (source_id);

CREATE TABLE chunks (
  id          UUID PRIMARY KEY,
  source_id   UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
  section_id  UUID REFERENCES sections(id) ON DELETE SET NULL,
  ordinal     INT NOT NULL,
  text        TEXT NOT NULL,
  token_count INT NOT NULL,
  page_from   INT,
  page_to     INT,
  char_from   INT NOT NULL,                 -- Offsets im Volltext → exaktes Zitat (P1)
  char_to     INT NOT NULL,
  embedding   VECTOR(1536),                 -- wird erst vom LlmGateway (M1) befüllt; kein JPA-Mapping bisher
  UNIQUE (source_id, ordinal)
);
CREATE INDEX chunks_embedding_hnsw ON chunks
  USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
CREATE INDEX ON chunks (source_id);
