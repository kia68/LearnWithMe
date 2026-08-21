package de.optadata.odil.learnwithme.authoring.internal.quality

import de.optadata.odil.learnwithme.ai.EmbeddingGateway
import de.optadata.odil.learnwithme.ai.LlmGateway
import de.optadata.odil.learnwithme.ai.LlmTask
import de.optadata.odil.learnwithme.ai.StructuredRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.math.sqrt

data class GroundednessResult(val grounded: Boolean, val similarity: Double, val judgeReason: String)

private data class JudgeVerdict(val grounded: Boolean, val reason: String)

private const val JUDGE_SYSTEM_PROMPT = """
Du prüfst, ob eine Lernfrage ausschließlich durch den gegebenen Textausschnitt belegt ist.
Antworte NUR mit validem JSON, ohne Erklärung außerhalb des JSON: {"grounded": true|false, "reason": "kurzer Grund auf Deutsch"}.
"grounded" ist nur dann true, wenn JEDE Tatsachenbehauptung in Frage und Erklärung direkt aus dem
Ausschnitt hervorgeht. Erfinde nichts, nimm keine Fakten aus deinem Weltwissen an — nur der
Ausschnitt zählt als Quelle.
"""

/**
 * ADR-008: hartes Qualitätstor. Zwei unabhängige Signale müssen beide zustimmen — Embedding-
 * Ähnlichkeit (billig, aber nur ein Proxy für thematische Nähe) UND ein LLM-Judge mit *nur* dem
 * zitierten Chunk als Kontext (teurer, aber prüft tatsächliche Faktentreue).
 */
@Component
class GroundednessGate(
    private val llmGateway: LlmGateway,
    private val embeddingGateway: EmbeddingGateway,
    @Value("\${learnwithme.quality.groundedness-similarity-threshold}") private val similarityThreshold: Double,
) {

    fun check(workspaceId: UUID, stem: String, explanation: String, sourceChunkText: String): GroundednessResult {
        val vectors = embeddingGateway.embed(workspaceId, listOf("$stem $explanation", sourceChunkText))
        val similarity = cosineSimilarity(vectors[0], vectors[1])

        val verdict = llmGateway.complete(
            StructuredRequest(
                workspaceId = workspaceId,
                task = LlmTask.GROUNDEDNESS_JUDGE,
                systemPrompt = JUDGE_SYSTEM_PROMPT,
                userPrompt = buildString {
                    appendLine("Zitierter Ausschnitt:")
                    appendLine("\"\"\"")
                    appendLine(sourceChunkText)
                    appendLine("\"\"\"")
                    appendLine()
                    appendLine("Frage: $stem")
                    appendLine("Erklärung: $explanation")
                },
                targetType = JudgeVerdict::class.java,
            ),
        ).value

        return GroundednessResult(
            grounded = similarity >= similarityThreshold && verdict.grounded,
            similarity = similarity,
            judgeReason = verdict.reason,
        )
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (sqrt(normA) * sqrt(normB))
    }
}
