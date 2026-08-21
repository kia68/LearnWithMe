package de.optadata.odil.learnwithme.ai.internal.llm

import com.openai.client.okhttp.OpenAIOkHttpClient
import de.optadata.odil.learnwithme.ai.EmbeddingGateway
import de.optadata.odil.learnwithme.ai.LlmTask
import de.optadata.odil.learnwithme.ai.internal.routing.ModelRouter
import de.optadata.odil.learnwithme.ai.internal.service.QuotaService
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.stereotype.Component
import java.util.UUID

/** Embeddings für Duplikaterkennung (C4) und spätere Vektorsuche. Nur OpenAI verdrahtet,
 * siehe [SpringAiLlmGateway]-Kommentar. */
@Component
class SpringAiEmbeddingGateway(
    private val modelRouter: ModelRouter,
    private val credentialResolver: CredentialResolver,
    private val quotaService: QuotaService,
    private val usageRecorder: LlmUsageRecorder,
) : EmbeddingGateway {

    override fun embed(workspaceId: UUID, texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        quotaService.assertWithinFreeQuota(workspaceId)
        val route = modelRouter.resolve(LlmTask.EMBEDDING)
        val key = credentialResolver.resolveOpenAiKey(workspaceId)

        val clientBuilder = OpenAIOkHttpClient.builder().apiKey(key.apiKey)
        key.baseUrl?.takeIf { it.isNotBlank() }?.let { clientBuilder.baseUrl(it) }
        val embeddingModel = OpenAiEmbeddingModel(clientBuilder.build())

        val vectors = embeddingModel.embed(texts)

        // Kein Token-Zähler auf diesem Pfad verfügbar — grobe Schätzung reicht für A4.
        val estimatedTokens = texts.sumOf { it.length / 4 }
        usageRecorder.record(workspaceId, LlmTask.EMBEDDING, route.provider, route.model, estimatedTokens, 0, estimatedTokens.toLong(), 0, "OK")

        return vectors
    }
}
