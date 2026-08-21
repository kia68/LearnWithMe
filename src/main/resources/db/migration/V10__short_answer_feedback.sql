-- Epic H (E4): SHORT_ANSWER wird asynchron per LLM-Rubric bewertet (GradeFreeTextJobHandler).
-- Das Feedback ist LLM-generierter, nicht-deterministischer Text — anders als bei allen anderen
-- Fragetypen (deren Begründung aus Item+Response jederzeit neu ableitbar ist, siehe ResponseGrader)
-- geht er ohne Speicherung beim einzigen Job-Lauf für immer verloren. Nullable und nur für
-- SHORT_ANSWER befüllt (siehe Attempt.kt-Kommentar).
ALTER TABLE attempts ADD COLUMN feedback TEXT;
