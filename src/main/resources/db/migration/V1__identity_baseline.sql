-- Epic A — Identität & Konto (§8.1, ADR-009)
-- E-Mail bewusst als TEXT statt CITEXT: Hibernates `ddl-auto=validate` erkennt den
-- citext-Spaltentyp nicht zuverlässig und schlägt beim Start fehl. Case-Insensitivität
-- kommt stattdessen aus dem funktionalen Unique-Index unten + Normalisierung (lowercase)
-- in der Anwendungsschicht (AuthService).
CREATE TABLE users (
  id            UUID PRIMARY KEY,
  email         TEXT NOT NULL,
  display_name  TEXT,
  locale        TEXT NOT NULL DEFAULT 'de',
  plan          TEXT NOT NULL DEFAULT 'FREE',
  password_hash TEXT,                     -- NULL bei reinen SSO-Konten
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX users_email_lower_uidx ON users (lower(email));

CREATE TABLE workspaces (               -- Tenant-Grenze; v1: 1 pro User
  id       UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name     TEXT NOT NULL
);
CREATE INDEX ON workspaces (owner_id);

-- Verknüpfte SSO-Identitäten (A1: Google/GitHub), erlaubt spätere Mehrfach-Verknüpfung
-- desselben Users mit mehreren Providern.
CREATE TABLE linked_identities (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider      TEXT NOT NULL,             -- GOOGLE | GITHUB
  provider_uid  TEXT NOT NULL,             -- subject/id beim Provider
  linked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_uid)
);

-- Refresh-Tokens sind opak (keine JWTs) und serverseitig widerrufbar (Rotation bei
-- jedem Refresh, Revocation bei Logout/Kontolöschung). Nur der Hash wird gespeichert.
CREATE TABLE refresh_tokens (
  id              UUID PRIMARY KEY,
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash      TEXT NOT NULL UNIQUE,
  expires_at      TIMESTAMPTZ NOT NULL,
  revoked_at      TIMESTAMPTZ,
  replaced_by_id  UUID REFERENCES refresh_tokens(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON refresh_tokens (user_id);

-- Nachweis der DSGVO-Löschung (A5). Kein FK auf users, da der User zu diesem
-- Zeitpunkt bereits gelöscht ist.
CREATE TABLE account_deletions (
  id           UUID PRIMARY KEY,
  user_id      UUID NOT NULL,
  email        TEXT NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
