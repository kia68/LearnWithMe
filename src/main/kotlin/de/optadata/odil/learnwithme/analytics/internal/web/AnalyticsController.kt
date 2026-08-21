package de.optadata.odil.learnwithme.analytics.internal.web

import de.optadata.odil.learnwithme.analytics.internal.config.AnalyticsProperties
import de.optadata.odil.learnwithme.analytics.internal.domain.Misconception
import de.optadata.odil.learnwithme.analytics.internal.service.MisconceptionQueryService
import de.optadata.odil.learnwithme.analytics.internal.service.WeeklyReport
import de.optadata.odil.learnwithme.analytics.internal.service.WeeklyReportGap
import de.optadata.odil.learnwithme.analytics.internal.service.WeeklyReportService
import de.optadata.odil.learnwithme.analytics.internal.web.dto.MisconceptionResponse
import de.optadata.odil.learnwithme.analytics.internal.web.dto.WeeklyReportGapResponse
import de.optadata.odil.learnwithme.analytics.internal.web.dto.WeeklyReportResponse
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** E3 (`GET /progress/misconceptions`, §9.2) und E5 (`GET /reports/weekly`). */
@RestController
class AnalyticsController(
    private val misconceptionQueryService: MisconceptionQueryService,
    private val weeklyReportService: WeeklyReportService,
    private val properties: AnalyticsProperties,
) {

    @GetMapping("/api/v1/progress/misconceptions")
    fun misconceptions(@AuthenticationPrincipal principal: TenantPrincipal): List<MisconceptionResponse> =
        misconceptionQueryService.listForUser(principal.workspaceId, principal.userId).map { it.toResponse() }

    @GetMapping("/api/v1/reports/weekly")
    fun weeklyReport(@AuthenticationPrincipal principal: TenantPrincipal): WeeklyReportResponse =
        weeklyReportService.weeklyReport(principal.workspaceId, principal.userId).toResponse()

    private fun Misconception.toResponse() = MisconceptionResponse(
        id = id,
        conceptId = conceptId,
        category = category.name,
        occurrences = occurrences,
        flagged = occurrences >= properties.misconceptionThreshold,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
    )

    private fun WeeklyReportGap.toResponse() = WeeklyReportGapResponse(conceptId, conceptName, mastery)

    private fun WeeklyReport.toResponse() = WeeklyReportResponse(topGaps.map { it.toResponse() }, recommendedFocus?.toResponse())
}
