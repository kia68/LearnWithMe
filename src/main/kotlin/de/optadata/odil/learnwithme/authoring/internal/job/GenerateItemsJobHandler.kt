package de.optadata.odil.learnwithme.authoring.internal.job

import com.fasterxml.jackson.databind.ObjectMapper
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemType
import de.optadata.odil.learnwithme.authoring.internal.generation.GenerationPipeline
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import de.optadata.odil.learnwithme.platform.JobHandler
import de.optadata.odil.learnwithme.platform.JobType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/** C1/C6: erzeugt `count` Items für ein Konzept, zyklisch über die angeforderten Typen und die
 * verfügbaren Belegstellen verteilt. Ein fehlgeschlagenes Einzelitem (LLM-Fehler, kaputtes JSON)
 * bricht die Charge nicht ab — wird geloggt, die restlichen Items werden trotzdem generiert. */
@Component
class GenerateItemsJobHandler(
    private val knowledgeApi: KnowledgeApi,
    private val generationPipeline: GenerationPipeline,
) : JobHandler {

    override val type = JobType.GENERATE_ITEMS
    private val log = LoggerFactory.getLogger(GenerateItemsJobHandler::class.java)
    private val objectMapper = ObjectMapper()

    override fun handle(jobId: UUID, payloadJson: String) {
        val node = objectMapper.readTree(payloadJson)
        val workspaceId = UUID.fromString(node.get("workspaceId").asText())
        val conceptId = UUID.fromString(node.get("conceptId").asText())
        val count = node.get("count").asInt()
        val requestedTypes = node.get("types").map { ItemType.valueOf(it.asText()) }
        val types = requestedTypes.ifEmpty { ItemType.entries }

        val evidenceChunkIds = knowledgeApi.listEvidence(conceptId).map { it.chunkId }
        if (evidenceChunkIds.isEmpty()) {
            log.warn("Konzept {} hat keine Belegstellen — Generierung übersprungen", conceptId)
            return
        }

        for (i in 0 until count) {
            val type = types[i % types.size]
            val chunkId = evidenceChunkIds[i % evidenceChunkIds.size]
            try {
                generationPipeline.generateOne(workspaceId, conceptId, type, chunkId)
            } catch (ex: Exception) {
                log.warn("Item-Generierung fehlgeschlagen (Konzept {}, Typ {}): {}", conceptId, type, ex.message, ex)
            }
        }
    }
}
