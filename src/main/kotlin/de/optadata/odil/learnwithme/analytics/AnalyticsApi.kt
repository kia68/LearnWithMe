package de.optadata.odil.learnwithme.analytics

import java.util.UUID

/** Ergebnis der E1-Klassifikation, für die sofortige Attempt-Antwort (§9.3 `errorAnalysis`).
 * [note] ist nur gesetzt, wenn sich daraus ein wiederkehrendes Muster ergibt (E3). */
data class ErrorAnalysisView(val category: String, val confidence: Float, val note: String?)

/**
 * Öffentlicher Port des `analytics`-Moduls (Epic E). Bewusst synchron statt über
 * Modulith-Events (PLAN.md §6.5 skizziert `AttemptRecorded` als asynchrones Event) — die
 * Klassifikation selbst ist reine, lastenfreie Logik ohne LLM (§11.5), es gibt also keinen
 * Latenzgrund für Async; nur die eigentliche Paraphrase-*Generierung* (LLM, E6) läuft asynchron
 * über die bestehende Job-Queue (ADR-012), von hier aus nur angestoßen.
 */
interface AnalyticsApi {
    /** Nur für `outcome != CORRECT` aufrufen. Klassifiziert (E1), persistiert das `ErrorEvent`,
     * pflegt die Misconception-Aggregation (E3) und stößt bei einer echten Wissenslücke
     * (nicht bei CARELESS/AMBIGUOUS_ITEM) asynchron eine Paraphrase-Generierung an (E6). */
    fun analyzeError(
        workspaceId: UUID,
        userId: UUID,
        attemptId: Long,
        itemId: UUID,
        conceptId: UUID,
        itemType: String,
        expectedSuccess: Float,
        elapsedMs: Int,
        thetaBefore: Float,
        itemDifficulty: Float,
        chosenOptionMisconceptionCategory: String?,
    ): ErrorAnalysisView
}
