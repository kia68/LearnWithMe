-- Epic G — Härtung: Postgres Row-Level Security als zweite Verteidigungslinie neben dem
-- bestehenden Tenant-Filter in jeder Query (N9, §15).
--
-- WICHTIG: `compose.yaml`s `POSTGRES_USER` wird vom offiziellen Postgres-Image beim `initdb`
-- als *Bootstrap-Superuser* angelegt. Superuser umgehen RLS immer, FORCE hin oder her (hartes
-- Postgres-Verhalten) — UND Postgres verweigert kategorisch, dem Bootstrap-Superuser SUPERUSER
-- wieder zu entziehen ("The bootstrap superuser must have the SUPERUSER attribute"), auch durch
-- sich selbst. Deshalb keine Demotion der bestehenden Rolle, sondern eine neue, gewöhnliche
-- Rolle `learnwithme_app` (kein Superuser, kein Owner) für den eigentlichen Anwendungsverkehr;
-- die bisherige Rolle bleibt Owner und wird ausschließlich von Flyway für Migrationen genutzt
-- (`spring.flyway.user`, unverändert). `spring.datasource.*` (JPA/Hikari, `TenantAwareDataSource`)
-- zeigt ab jetzt auf `learnwithme_app` (application.yml).
--
-- Dev-Default-Passwort unten, analog zu `compose.yaml`s hartkodierten `learnwithme`/`learnwithme`-
-- Zugangsdaten — für echte Deployments per `ALTER ROLE learnwithme_app PASSWORD '...'` rotieren
-- und `DB_APP_PASSWORD` entsprechend setzen.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'learnwithme_app') THEN
    CREATE ROLE learnwithme_app LOGIN PASSWORD 'learnwithme_app_dev';
  END IF;
END $$;

-- `current_database()` statt eines hartkodierten Namens: die echte DB heißt "learnwithme" nur in
-- `compose.yaml`s Dev-Setup — Testcontainers (Tests, siehe AbstractIntegrationTest) vergibt
-- standardmäßig "test". `GRANT ... ON DATABASE` erlaubt an dieser Stelle kein Funktionsergebnis
-- direkt, daher `EXECUTE format(...)`.
DO $$
BEGIN
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO learnwithme_app', current_database());
END $$;
GRANT USAGE ON SCHEMA public TO learnwithme_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO learnwithme_app;
ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO learnwithme_app;

-- `app.workspace_id` wird pro DB-Connection-Checkout von `TenantAwareDataSource` gesetzt (leer,
-- wenn kein Tenant-Kontext aktiv ist, z.B. Login/Register). Vergleich als TEXT statt eines
-- `::uuid`-Casts auf `current_setting(...)`: ein leerer/ungesetzter Wert wirft so nie einen
-- Laufzeitfehler, sondern vergleicht schlicht nie gleich einer echten UUID (default-deny).

-- Hintergrund-Jobs (platform.internal.job.JobWorker) laufen außerhalb einer HTTP-Anfrage und
-- brauchen daher ihren eigenen Tenant-Bezug direkt auf dem Job-Datensatz, um `TenantContext`
-- vor der Job-Bearbeitung setzen zu können. `jobs` selbst bleibt bewusst ohne RLS-Policy — es
-- ist eine plattforminterne Warteschlange ohne fachlichen Lese-Endpunkt für Nutzer.
DELETE FROM jobs; -- transiente Warteschlangen-Einträge, keine fachlichen Daten
ALTER TABLE jobs ADD COLUMN workspace_id UUID NOT NULL;

DO $$
DECLARE
  t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'ai_credentials', 'llm_usage', 'sources', 'concepts', 'items',
    'sessions', 'attempts', 'learner_concept_state', 'error_events', 'misconceptions'
  ]
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON %I USING (workspace_id::text = current_setting(''app.workspace_id'', true)) WITH CHECK (workspace_id::text = current_setting(''app.workspace_id'', true))',
      t
    );
  END LOOP;
END $$;
