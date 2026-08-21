-- Epic E — Echtzeit-Fehlerkorrektur & -analyse (§8.1, §11.5). Neues Modul `analytics`.
CREATE TABLE error_events (
  id           BIGSERIAL PRIMARY KEY,
  workspace_id UUID NOT NULL,
  attempt_id   BIGINT NOT NULL,
  user_id      UUID NOT NULL,
  concept_id   UUID NOT NULL,
  item_id      UUID NOT NULL,
  category     TEXT NOT NULL,               -- §11.5: FACTUAL_GAP|TERM_CONFUSION|CONCEPT_CONFUSION|PROCEDURAL|CARELESS|AMBIGUOUS_ITEM
  detail       TEXT,
  confidence   REAL NOT NULL,
  detected_by  TEXT NOT NULL,                -- RATIONALE | HEURISTIC (kein LLM zur Laufzeit, §11.5)
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON error_events (workspace_id, user_id, concept_id, category);
CREATE INDEX ON error_events (item_id);

-- Abweichung von PLAN.md §8.1: `category` statt freiem `label` — wir haben keine über die
-- feste Taxonomie (§11.5) hinausgehenden Misconception-Labels zu unterscheiden.
CREATE TABLE misconceptions (
  id            UUID PRIMARY KEY,
  workspace_id  UUID NOT NULL,
  user_id       UUID NOT NULL,
  concept_id    UUID NOT NULL,
  category      TEXT NOT NULL,
  occurrences   INT NOT NULL DEFAULT 1,
  first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at   TIMESTAMPTZ,
  UNIQUE (user_id, concept_id, category)
);
CREATE INDEX ON misconceptions (workspace_id, user_id, resolved_at);
