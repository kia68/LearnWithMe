-- Spring Modulith Event-Publication-Registry (ADR-001/ADR-012: Outbox über Modulith-Events).
-- `spring-modulith-starter-jpa` erwartet diese Tabelle, erzeugt sie aber nicht selbst — dafür gäbe
-- es `spring.modulith.events.jdbc.schema-initialization.enabled=true`, das wir bewusst NICHT
-- setzen: gleiche Begründung wie bei der `pgvector`-Extension/den HNSW-Indizes (§8.3) — Schema
-- kommt ausschließlich aus Flyway, damit Änderungen nachvollziehbar bleiben. Kanonisches DDL für
-- PostgreSQL aus der Spring-Modulith-Referenzdokumentation (Appendix) übernommen.
CREATE TABLE event_publication (
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMPTZ NOT NULL,
  completion_date        TIMESTAMPTZ,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMPTZ,
  PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
