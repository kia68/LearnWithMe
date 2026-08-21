-- Epic D — Adaptives Frage-Antwort-System (§8.1, §11, ADR-005, ADR-006)
-- Neue Module `assessment` (sessions/attempts) und `adaptivity` (learner_concept_state).

-- D6: Item-Qualitätssignal aus Skips, analog zu `report_count` (Epic C).
ALTER TABLE items ADD COLUMN skip_count INT NOT NULL DEFAULT 0;

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
  summary       TEXT                        -- Jackson-JSON, gleiche Abwägung wie items.payload (Epic C)
);
CREATE INDEX ON sessions (workspace_id, user_id, started_at DESC);

CREATE TABLE attempts (                     -- append-only (N14)
  id           BIGSERIAL PRIMARY KEY,
  workspace_id UUID NOT NULL,
  session_id   UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  user_id      UUID NOT NULL,
  item_id      UUID NOT NULL REFERENCES items(id),
  item_type    TEXT NOT NULL,               -- denormalisiert aus items.type (Typ-Rotation, §11.3)
  concept_id   UUID NOT NULL,
  response     TEXT NOT NULL,               -- Jackson-JSON der rohen Antwort
  outcome      TEXT NOT NULL,               -- CORRECT | PARTIAL | INCORRECT | SKIPPED
  score        REAL NOT NULL,               -- 0..1
  elapsed_ms   INT NOT NULL,
  theta_before REAL NOT NULL,
  theta_after  REAL NOT NULL,
  p_expected   REAL NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON attempts (user_id, concept_id, created_at DESC);
CREATE INDEX ON attempts (session_id, created_at DESC);
CREATE INDEX ON attempts (item_id);

-- ═══════════════ adaptivity ═══════════════
-- Abweichung von PLAN.md §8.1: zusätzliche `workspace_id`-Spalte für die zweite
-- Mandanten-Verteidigungslinie (N9), analog zu jeder anderen mandantenfähigen Tabelle
-- in dieser Codebase (items, concepts, sessions, ...) — PLAN.md hatte sie hier vergessen.
CREATE TABLE learner_concept_state (
  workspace_id UUID NOT NULL,
  user_id      UUID NOT NULL,
  concept_id   UUID NOT NULL REFERENCES concepts(id) ON DELETE CASCADE,
  theta        REAL NOT NULL DEFAULT 0.0,
  theta_n      INT  NOT NULL DEFAULT 0,
  mastery      REAL NOT NULL DEFAULT 0.5,
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
CREATE INDEX ON learner_concept_state (workspace_id, user_id, due_at);
