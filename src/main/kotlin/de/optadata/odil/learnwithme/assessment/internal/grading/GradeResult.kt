package de.optadata.odil.learnwithme.assessment.internal.grading

import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome

data class GradeResult(
    val score: Float,
    val outcome: AttemptOutcome,
    val correctResponseJson: String,
    val chosenOptionRationale: String?,
)
