package de.optadata.odil.learnwithme.assessment.internal.grading

import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome

/** Gemeinsame Score→Outcome-Schwellen für jedes Grading, deterministisch (§10.3) wie
 * LLM-Rubric-basiert (Epic H, [de.optadata.odil.learnwithme.assessment.internal.job.GradeFreeTextJobHandler]). */
internal fun outcomeForScore(score: Float): AttemptOutcome = when {
    score >= 0.999f -> AttemptOutcome.CORRECT
    score <= 0.001f -> AttemptOutcome.INCORRECT
    else -> AttemptOutcome.PARTIAL
}

data class GradeResult(
    val score: Float,
    val outcome: AttemptOutcome,
    val correctResponseJson: String,
    val chosenOptionRationale: String?,
    /** E1: `misconceptionCategory` der gewählten (falschen) Option, falls vorhanden — Brücke zur
     * Fehleranalyse ohne Laufzeit-LLM. Nur für MC_SINGLE/MC_MULTI gesetzt. */
    val chosenOptionMisconceptionCategory: String? = null,
)
