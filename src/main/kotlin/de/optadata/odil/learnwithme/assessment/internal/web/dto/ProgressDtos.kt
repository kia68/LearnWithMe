package de.optadata.odil.learnwithme.assessment.internal.web.dto

import java.time.Instant
import java.util.UUID

data class ProgressOverviewResponse(val conceptCount: Int, val averageMastery: Float, val dueCount: Int)

data class ConceptProgressResponse(
    val conceptId: UUID,
    val theta: Float,
    val mastery: Float,
    val state: String,
    val reps: Int,
    val lapses: Int,
    val dueAt: Instant?,
)
