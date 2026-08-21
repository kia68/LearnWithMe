package de.optadata.odil.learnwithme.authoring.internal.job

import com.fasterxml.jackson.databind.ObjectMapper
import de.optadata.odil.learnwithme.authoring.internal.generation.GenerationPipeline
import de.optadata.odil.learnwithme.platform.JobHandler
import de.optadata.odil.learnwithme.platform.JobType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/** E6: erzeugt eine Paraphrase-Variante eines Items (asynchron, ADR-012 — kein LLM im
 * Antwort-kritischen Pfad, N1). Ausgelöst von `analytics` nach einer Fehlklassifikation. */
@Component
class GenerateParaphraseJobHandler(private val generationPipeline: GenerationPipeline) : JobHandler {

    override val type = JobType.GENERATE_PARAPHRASE
    private val log = LoggerFactory.getLogger(GenerateParaphraseJobHandler::class.java)
    private val objectMapper = ObjectMapper()

    override fun handle(jobId: UUID, payloadJson: String) {
        val node = objectMapper.readTree(payloadJson)
        val workspaceId = UUID.fromString(node.get("workspaceId").asText())
        val originalItemId = UUID.fromString(node.get("originalItemId").asText())
        try {
            generationPipeline.generateParaphrase(workspaceId, originalItemId)
        } catch (ex: Exception) {
            log.warn("Paraphrase-Generierung fehlgeschlagen (Original-Item {}): {}", originalItemId, ex.message, ex)
        }
    }
}
