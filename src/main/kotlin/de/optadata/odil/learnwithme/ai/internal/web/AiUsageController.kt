package de.optadata.odil.learnwithme.ai.internal.web

import de.optadata.odil.learnwithme.ai.internal.service.AiUsageService
import de.optadata.odil.learnwithme.ai.internal.web.dto.UsageSummaryResponse
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.TemporalAdjusters
import java.time.ZoneOffset

/** A4: Token-/Kostenverbrauch pro Workspace und Monat. */
@RestController
@RequestMapping("/api/v1/ai/usage")
class AiUsageController(private val usageService: AiUsageService) {

    @GetMapping
    fun usage(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
    ): UsageSummaryResponse {
        val effectiveTo = to ?: Instant.now()
        val effectiveFrom = from ?: startOfCurrentMonth()
        return usageService.summary(principal.workspaceId, effectiveFrom, effectiveTo)
    }

    private fun startOfCurrentMonth(): Instant =
        Instant.now().atZone(ZoneOffset.UTC).with(TemporalAdjusters.firstDayOfMonth())
            .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
}
