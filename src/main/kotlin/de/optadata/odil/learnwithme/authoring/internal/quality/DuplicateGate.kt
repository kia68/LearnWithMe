package de.optadata.odil.learnwithme.authoring.internal.quality

import de.optadata.odil.learnwithme.ai.EmbeddingGateway
import de.optadata.odil.learnwithme.authoring.internal.persistence.ItemRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

class DuplicateCheckResult(val isDuplicate: Boolean, val embedding: FloatArray, val duplicateOfItemId: UUID?)

/** C4: Embedding-Kosinus-Ähnlichkeit über Schwelle gegen die Item-Bank desselben Dokuments → Duplikat. */
@Component
class DuplicateGate(
    private val embeddingGateway: EmbeddingGateway,
    private val itemRepository: ItemRepository,
    @Value("\${learnwithme.quality.duplicate-similarity-threshold}") private val duplicateThreshold: Double,
) {
    fun check(workspaceId: UUID, conceptId: UUID, stem: String): DuplicateCheckResult {
        val embedding = embeddingGateway.embed(workspaceId, listOf(stem)).first()
        val duplicateId = itemRepository.findNearestDuplicateId(conceptId, PgVectorFormat.toLiteral(embedding), duplicateThreshold)
        return DuplicateCheckResult(duplicateId != null, embedding, duplicateId)
    }
}
