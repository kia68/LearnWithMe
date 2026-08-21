-- Epic A — BYOK & Kostenkontrolle (§8.1, ADR-009, ADR-010)
CREATE TABLE ai_credentials (
  id               UUID PRIMARY KEY,
  workspace_id     UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  provider         TEXT NOT NULL,          -- OPENAI | ANTHROPIC | GOOGLE | AZURE | OPENROUTER | OLLAMA
  label            TEXT,
  ciphertext       BYTEA NOT NULL,         -- AES-256-GCM(api_key, DEK)
  wrapped_dek      BYTEA NOT NULL,         -- KEK-verschlüsselter Data Key (IV-Präfix inklusive)
  nonce            BYTEA NOT NULL,         -- GCM-IV für ciphertext
  key_hint         TEXT NOT NULL,          -- z.B. "…a3f9" — einziger Rückgabewert
  base_url         TEXT,                   -- für Ollama / Self-Hosted / Azure
  region           TEXT,                   -- für N10 (Datenfluss-Anzeige)
  last_verified_at TIMESTAMPTZ,
  status           TEXT NOT NULL DEFAULT 'UNVERIFIED', -- UNVERIFIED | VERIFIED | INVALID
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (workspace_id, provider, label)
);
CREATE INDEX ON ai_credentials (workspace_id);

CREATE TABLE llm_usage (                  -- N13, §16; wird ab dem ai-LlmGateway (M1) befüllt
  id             BIGSERIAL PRIMARY KEY,
  workspace_id   UUID NOT NULL,
  task           TEXT NOT NULL,            -- CONCEPT_EXTRACTION | ITEM_GENERATION | JUDGE | …
  provider       TEXT NOT NULL,
  model          TEXT NOT NULL,
  input_tokens   INT NOT NULL,
  output_tokens  INT NOT NULL,
  cached_tokens  INT NOT NULL DEFAULT 0,
  cost_micros    BIGINT NOT NULL,          -- Mikro-Euro, keine Floats bei Geld
  latency_ms     INT NOT NULL,
  outcome        TEXT NOT NULL,            -- OK | GATE_REJECTED | ERROR
  correlation_id UUID,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON llm_usage (workspace_id, created_at DESC);
