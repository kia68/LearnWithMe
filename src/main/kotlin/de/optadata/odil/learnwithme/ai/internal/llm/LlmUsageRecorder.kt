package de.optadata.odil.learnwithme.ai.internal.llm

import de.optadata.odil.learnwithme.ai.LlmTask
import de.optadata.odil.learnwithme.ai.internal.domain.LlmUsageRecord
import de.optadata.odil.learnwithme.ai.internal.persistence.LlmUsageRepository
import org.springframework.stereotype.Component
import java.util.UUID

/** Schreibt jeden LLM-Aufruf nach `llm_usage` (N13, A4) — bislang nur Lese-/Aggregationsseite
 * existierte (Epic A: [de.optadata.odil.learnwithme.ai.internal.service.AiUsageService]). */
@Component
class LlmUsageRecorder(private val usageRepository: LlmUsageRepository) {

    fun record(
        workspaceId: UUID,
        task: LlmTask,
        provider: String,
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        costMicros: Long,
        latencyMs: Long,
        outcome: String,
    ) {
        usageRepository.save(
            LlmUsageRecord(
                workspaceId = workspaceId,
                task = task.name,
                provider = provider,
                model = model,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                costMicros = costMicros,
                latencyMs = latencyMs.toInt(),
                outcome = outcome,
            ),
        )
    }
}
