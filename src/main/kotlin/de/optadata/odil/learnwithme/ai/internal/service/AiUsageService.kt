package de.optadata.odil.learnwithme.ai.internal.service

import de.optadata.odil.learnwithme.ai.internal.persistence.LlmUsageRepository
import de.optadata.odil.learnwithme.ai.internal.web.dto.UsageBreakdownEntry
import de.optadata.odil.learnwithme.ai.internal.web.dto.UsageSummaryResponse
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/** A4: Token-/Kostenverbrauch pro Workspace. Gruppiert nach `task` als Kostentreiber,
 * da eine Zuordnung zu einzelnen Dokumenten erst mit dem `content`-Modul (Epic B)
 * existiert. */
@Service
class AiUsageService(
    private val usageRepository: LlmUsageRepository,
    private val quotaService: QuotaService,
) {

    fun summary(workspaceId: UUID, from: Instant, to: Instant): UsageSummaryResponse {
        val records = usageRepository.findAllByWorkspaceIdAndCreatedAtBetween(workspaceId, from, to)

        val topCostDrivers = records.groupBy { it.task }
            .map { (task, entries) ->
                UsageBreakdownEntry(
                    task = task,
                    costMicros = entries.sumOf { it.costMicros },
                    inputTokens = entries.sumOf { it.inputTokens.toLong() },
                    outputTokens = entries.sumOf { it.outputTokens.toLong() },
                )
            }
            .sortedByDescending { it.costMicros }
            .take(5)

        return UsageSummaryResponse(
            from = from,
            to = to,
            totalInputTokens = records.sumOf { it.inputTokens.toLong() },
            totalOutputTokens = records.sumOf { it.outputTokens.toLong() },
            totalCostMicros = records.sumOf { it.costMicros },
            topCostDrivers = topCostDrivers,
            quota = quotaService.quotaInfo(workspaceId),
        )
    }
}
