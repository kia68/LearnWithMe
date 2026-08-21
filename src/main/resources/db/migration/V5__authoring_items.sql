-- Epic C — Fragegenerierung (Authoring) (§8.1, ADR-007, ADR-008)
-- `payload`/`quality` bewusst TEXT statt JSONB: gleiche Abwägung wie `jobs.payload` (V3) —
-- Hibernates jsonb-Schreibpfad ist ohne laufende Postgres-Instanz hier nicht verifizierbar.
CREATE TABLE items (
  id                UUID PRIMARY KEY,
  workspace_id      UUID NOT NULL,
  concept_id        UUID NOT NULL REFERENCES concepts(id) ON DELETE CASCADE,
  parent_item_id    UUID REFERENCES items(id),          -- Paraphrase-Variante (E6, hier ungenutzt)
  type              TEXT NOT NULL,                       -- §10: MC_SINGLE|MC_MULTI|TRUE_FALSE|ORDERING|MATCHING|CLOZE
  stem              TEXT NOT NULL,
  payload           TEXT NOT NULL,                       -- Jackson-JSON, ADR-007
  explanation       TEXT NOT NULL,
  bloom_level       TEXT NOT NULL,                        -- REMEMBER|UNDERSTAND|APPLY|ANALYZE|EVALUATE|CREATE
  language          TEXT NOT NULL,
  -- Beleg (P1, ADR-008)
  source_chunk_id   UUID NOT NULL REFERENCES chunks(id),
  source_char_from  INT NOT NULL,
  source_char_to    INT NOT NULL,
  -- Kalibrierung (§11) — erst ab Epic D befüllt
  difficulty        REAL NOT NULL DEFAULT 0.0,
  difficulty_n      INT  NOT NULL DEFAULT 0,
  p_correct         REAL,
  -- Qualität (ADR-008)
  status            TEXT NOT NULL DEFAULT 'DRAFT',        -- DRAFT|PUBLISHED|REJECTED|RETIRED
  quality           TEXT NOT NULL DEFAULT '{}',           -- Gate-Ergebnisse + Scores (Jackson-JSON)
  report_count      INT NOT NULL DEFAULT 0,
  embedding         VECTOR(1536),                          -- Duplikaterkennung (C4)
  generated_by      TEXT,                                  -- Modell + Task
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON items (concept_id, status, difficulty);
CREATE INDEX items_embedding_hnsw ON items USING hnsw (embedding vector_cosine_ops);

CREATE TABLE item_reports (                               -- C5: 1-Klick-Report mit Grund
  id         UUID PRIMARY KEY,
  item_id    UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  user_id    UUID NOT NULL,
  reason     TEXT NOT NULL,                                -- WRONG|UNCLEAR|TRIVIAL|NOT_IN_TEXT|OTHER
  comment    TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON item_reports (item_id);
