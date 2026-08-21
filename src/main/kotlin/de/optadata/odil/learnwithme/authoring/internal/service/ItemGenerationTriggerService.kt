package de.optadata.odil.learnwithme.authoring.internal.service

import de.optadata.odil.learnwithme.authoring.internal.domain.ItemType
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import de.optadata.odil.learnwithme.platform.JobQueue
import de.optadata.odil.learnwithme.platform.JobType
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

/** C1/C6: reiht einen Generierungs-Job ein — jeder Aufruf ist ein neuer Job (kein Dedup über
 * `jobKey`, da wiederholtes "Mehr üben" auf demselben Konzept ausdrücklich erwünscht ist, C6). */
@Service
class ItemGenerationTriggerService(
    private val knowledgeApi: KnowledgeApi,
    private val jobQueue: JobQueue,
) {
    fun trigger(workspaceId: UUID, conceptId: UUID, count: Int, types: List<ItemType>): UUID {
        val concept = knowledgeApi.getConcept(conceptId)
        if (concept.workspaceId != workspaceId) throw NotFoundException("Konzept $conceptId nicht gefunden")

        val payload = mapOf(
            "workspaceId" to workspaceId.toString(),
            "conceptId" to conceptId.toString(),
            "count" to count,
            "types" to types.map { it.name },
        )
        return jobQueue.enqueue(JobType.GENERATE_ITEMS, "generate:$conceptId:${System.currentTimeMillis()}", workspaceId, payload)
    }
}
