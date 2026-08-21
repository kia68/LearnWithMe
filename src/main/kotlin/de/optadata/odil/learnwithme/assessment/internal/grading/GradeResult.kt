package de.optadata.odil.learnwithme.assessment.internal.grading

import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome

data class GradeResult(
    val score: Float,
    val outcome: AttemptOutcome,
    val correctResponseJson: String,
    val chosenOptionRationale: String?,
    /** E1: `misconceptionCategory` der gewählten (falschen) Option, falls vorhanden — Brücke zur
     * Fehleranalyse ohne Laufzeit-LLM. Nur für MC_SINGLE/MC_MULTI gesetzt. */
    val chosenOptionMisconceptionCategory: String? = null,
)
