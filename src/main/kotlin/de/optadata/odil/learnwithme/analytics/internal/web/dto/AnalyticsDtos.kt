package de.optadata.odil.learnwithme.analytics.internal.web.dto

import java.time.Instant
import java.util.UUID

data class MisconceptionResponse(
    val id: UUID,
    val conceptId: UUID,
    val category: String,
    val occurrences: Int,
    val flagged: Boolean,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
)

data class WeeklyReportGapResponse(val conceptId: UUID, val conceptName: String, val mastery: Float)

data class WeeklyReportResponse(val topGaps: List<WeeklyReportGapResponse>, val recommendedFocus: WeeklyReportGapResponse?)
