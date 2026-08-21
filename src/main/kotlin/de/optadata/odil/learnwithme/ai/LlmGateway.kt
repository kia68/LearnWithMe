package de.optadata.odil.learnwithme.ai

import java.util.UUID

/** Task-Klassen für Modell-Routing (ADR-010). */
enum class LlmTask {
    CONCEPT_EXTRACTION,
    EMBEDDING,
    ITEM_GENERATION,
    GROUNDEDNESS_JUDGE,
    FREE_TEXT_GRADING,
    ERROR_ANALYSIS,
}

data class TextRequest(val workspaceId: UUID, val task: LlmTask, val systemPrompt: String, val userPrompt: String)

data class StructuredRequest<T : Any>(
    val workspaceId: UUID,
    val task: LlmTask,
    val systemPrompt: String,
    val userPrompt: String,
    val targetType: Class<T>,
)

data class LlmResult<T>(
    val value: T,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val costMicros: Long,
    val latencyMs: Long,
)

/**
 * Öffentlicher Port des `ai`-Moduls (ADR-004). Spring AI ist ausschließlich die *Implementierung*
 * dahinter (`ai.internal.llm`) — kein Spring-AI-Typ taucht in dieser Signatur auf, damit ein
 * Framework-Wechsel/-Breaking-Change lokal bleibt.
 *
 * Bewusst blockierend statt `suspend` (Abweichung vom Sketch in ADR-004): Controller (Spring MVC)
 * und der Job-Worker (`platform.internal.job.JobWorker`) sind beide synchron: `kotlinx-coroutines`
 * ist als Abhängigkeit vorhanden, wird aber in der gesamten bisherigen Codebase (Epic A/B) nirgends
 * genutzt — `suspend` hier hätte nur `runBlocking`-Brücken an jeder Aufrufstelle erzwungen, ohne
 * echten Nutzen.
 */
interface LlmGateway {
    fun completeText(req: TextRequest): LlmResult<String>
    fun <T : Any> complete(req: StructuredRequest<T>): LlmResult<T>
}

interface EmbeddingGateway {
    fun embed(workspaceId: UUID, texts: List<String>): List<FloatArray>
}
