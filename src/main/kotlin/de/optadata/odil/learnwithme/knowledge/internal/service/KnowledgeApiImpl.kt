package de.optadata.odil.learnwithme.knowledge.internal.service

import de.optadata.odil.learnwithme.knowledge.ConceptSummary
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import de.optadata.odil.learnwithme.knowledge.internal.persistence.ConceptRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class KnowledgeApiImpl(private val conceptRepository: ConceptRepository) : KnowledgeApi {

    override fun listConcepts(sourceId: UUID): List<ConceptSummary> =
        conceptRepository.findAllBySourceIdOrderByFrequencyDesc(sourceId)
            .map { ConceptSummary(it.id, it.name, it.frequency) }
}
