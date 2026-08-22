-- Härtung: V9 grantete `learnwithme_app` SELECT/INSERT/UPDATE/DELETE auf ALLE TABELLEN, aber
-- Sequenzen sind in Postgres eigene Objekte — ein `INSERT` in eine Tabelle mit `BIGSERIAL`-Spalte
-- braucht zusätzlich USAGE auf die zugrunde liegende Sequenz, sonst "permission denied for
-- sequence ...". Betroffen: `llm_usage`, `attempts`, `error_events` (die einzigen drei Tabellen
-- mit BIGSERIAL-PK statt UUID) — d.h. seit V9 schlug JEDE Antwortabgabe (D4, der kritische Pfad!)
-- unter der `learnwithme_app`-Rolle fehl. Unbemerkt, weil bislang kein echter `POST attempts`-Call
-- gegen den RLS-aktivierten Dev-Stack gelaufen war (Epic H testete nur den SHORT_ANSWER-Pfad, der
-- vor dem eigentlichen INSERT am fehlenden LLM-Key scheiterte) — erst bei der Live-Verifikation der
-- M6-Nachtrag-Fragetypen (NUMERIC/CATEGORIZATION/CODE_OUTPUT) hier tatsächlich aufgetreten.
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO learnwithme_app;
ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
  GRANT USAGE ON SEQUENCES TO learnwithme_app;
