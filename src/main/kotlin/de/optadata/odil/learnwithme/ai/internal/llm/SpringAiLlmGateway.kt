package de.optadata.odil.learnwithme.ai.internal.llm

import de.optadata.odil.learnwithme.ai.LlmGateway
import de.optadata.odil.learnwithme.ai.LlmResult
import de.optadata.odil.learnwithme.ai.LlmTask
import de.optadata.odil.learnwithme.ai.StructuredRequest
import de.optadata.odil.learnwithme.ai.TextRequest
import de.optadata.odil.learnwithme.ai.internal.routing.ModelRouter
import de.optadata.odil.learnwithme.ai.internal.service.QuotaService
import de.optadata.odil.learnwithme.shared.BadGatewayException
import de.optadata.odil.learnwithme.shared.JsonMapper
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Spring-AI-Implementierung des [LlmGateway] (ADR-004). Spring-AI-Typen (`ChatClient`,
 * `OpenAiChatModel`, …) bleiben strikt in `ai.internal.llm` — außerhalb sieht niemand mehr als
 * [LlmGateway].
 *
 * Structured Output bewusst NICHT über Spring AIs `BeanOutputConverter`/`.entity()`
 * (ADR-004 nennt genau das als Bruchstellen-Quelle über Milestone-Versionen hinweg): stattdessen
 * fordert der System-Prompt explizit JSON an, die Antwort wird mit reinem Jackson geparst —
 * derselbe robuste, bereits bewährte Pfad wie in `identity.internal.oauth.OAuth2CodeExchangeService`.
 *
 * Nur OpenAI konkret verdrahtet: die Default-Routing-Konfiguration (`learnwithme.ai-routing.*`)
 * zeigt für jede Task-Klasse auf `openai`. Anthropic/Ollama folgen demselben Muster, sind aber
 * ohne konkreten Story-Bedarf hier nicht gebaut (siehe docs/progress.md).
 */
@Component
class SpringAiLlmGateway(
    private val modelRouter: ModelRouter,
    private val credentialResolver: CredentialResolver,
    private val quotaService: QuotaService,
    private val usageRecorder: LlmUsageRecorder,
) : LlmGateway {

    private val objectMapper = JsonMapper.instance

    override fun completeText(req: TextRequest): LlmResult<String> {
        val (content, meta) = call(req.workspaceId, req.task, req.systemPrompt, req.userPrompt)
        return LlmResult(content, meta.model, meta.inputTokens, meta.outputTokens, meta.costMicros, meta.latencyMs)
    }

    override fun <T : Any> complete(req: StructuredRequest<T>): LlmResult<T> {
        val (content, meta) = call(req.workspaceId, req.task, req.systemPrompt, req.userPrompt)
        val value = try {
            objectMapper.readValue(content, req.targetType)
        } catch (ex: Exception) {
            throw BadGatewayException("LLM-Antwort war kein valides JSON für ${req.targetType.simpleName}: ${ex.message}")
        }
        return LlmResult(value, meta.model, meta.inputTokens, meta.outputTokens, meta.costMicros, meta.latencyMs)
    }

    private data class CallMeta(val model: String, val inputTokens: Int, val outputTokens: Int, val costMicros: Long, val latencyMs: Long)

    private fun call(workspaceId: UUID, task: LlmTask, systemPrompt: String, userPrompt: String): Pair<String, CallMeta> {
        quotaService.assertWithinFreeQuota(workspaceId)
        val route = modelRouter.resolve(task)
        val key = credentialResolver.resolveOpenAiKey(workspaceId)
        val chatClient = ChatClient.create(buildChatModel(key))

        val started = Instant.now()
        val response = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .options(OpenAiChatOptions.builder().model(route.model))
            .call()
            .chatResponse()
        val latencyMs = Duration.between(started, Instant.now()).toMillis()

        val content = response?.result?.output?.text
            ?: throw BadGatewayException("Provider ${route.provider} lieferte keine Antwort.")
        val usage = response.metadata.usage
        val inputTokens = usage?.promptTokens ?: 0
        val outputTokens = usage?.completionTokens ?: 0
        val costMicros = estimateCostMicros(route.model, inputTokens, outputTokens)

        usageRecorder.record(workspaceId, task, route.provider, route.model, inputTokens, outputTokens, costMicros, latencyMs, "OK")

        return content to CallMeta(route.model, inputTokens, outputTokens, costMicros, latencyMs)
    }

    private fun buildChatModel(key: ResolvedKey): OpenAiChatModel =
        OpenAiChatModel.builder().openAiClient(buildOpenAiClient(key)).build()

    private fun buildOpenAiClient(key: ResolvedKey): OpenAIClient {
        val builder = OpenAIOkHttpClient.builder().apiKey(key.apiKey)
        key.baseUrl?.takeIf { it.isNotBlank() }?.let { builder.baseUrl(it) }
        return builder.build()
    }

    /** Grobe, konservative Preistabelle (Mikro-Euro/Token) — dient nur A4/A6-Kostenanzeige,
     * keine Abrechnungsgrundlage. Providerpreise ändern sich; exakte Kalibrierung ist kein
     * Epic-C-Story-Bedarf. */
    private fun estimateCostMicros(model: String, inputTokens: Int, outputTokens: Int): Long {
        val (inRate, outRate) = when {
            model.startsWith("gpt-4o-mini") -> 0.14 to 0.56
            model.startsWith("gpt-4o") -> 2.3 to 9.2
            else -> 1.0 to 3.0
        }
        return (inputTokens * inRate + outputTokens * outRate).toLong()
    }
}
