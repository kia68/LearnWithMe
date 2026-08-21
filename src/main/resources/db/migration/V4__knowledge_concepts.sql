-- Epic B (B8) — Kernkonzepte je Dokument, ausgewertet aus `content.chunks`
-- nach Abschluss der Indizierung (Event `SourceIndexed`).
CREATE TABLE concepts (
  id           UUID PRIMARY KEY,
  workspace_id UUID NOT NULL,
  source_id    UUID REFERENCES sources(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  summary      TEXT NOT NULL,
  frequency    INT NOT NULL DEFAULT 1,      -- B8: Häufigkeit im Dokument
  embedding    VECTOR(1536),                -- wird erst vom LlmGateway (M1) befüllt; kein JPA-Mapping bisher
  importance   REAL NOT NULL DEFAULT 0.5,   -- 0..1, steuert späteres Generierungsbudget (Epic C)
  item_target  INT  NOT NULL DEFAULT 5,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source_id, name)
);
CREATE INDEX concepts_embedding_hnsw ON concepts USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON concepts (source_id);

-- Surrogater UUID-PK statt zusammengesetztem Schlüssel: einfachere, konsistente
-- JPA-Abbildung (jede andere Entität in dieser Codebase hat ebenfalls einen UUID-PK).
CREATE TABLE concept_evidence (              -- B8: Belegstellen je Konzept
  id         UUID PRIMARY KEY,
  concept_id UUID NOT NULL REFERENCES concepts(id) ON DELETE CASCADE,
  chunk_id   UUID NOT NULL REFERENCES chunks(id)   ON DELETE CASCADE,
  weight     REAL NOT NULL DEFAULT 1.0,
  UNIQUE (concept_id, chunk_id)
);
