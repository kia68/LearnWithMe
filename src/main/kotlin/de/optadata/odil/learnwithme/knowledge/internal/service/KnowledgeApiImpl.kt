package de.optadata.odil.learnwithme.knowledge.internal.service

import de.optadata.odil.learnwithme.knowledge.ConceptDetail
import de.optadata.odil.learnwithme.knowledge.ConceptEvidenceView
import de.optadata.odil.learnwithme.knowledge.ConceptSummary
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import de.optadata.odil.learnwithme.knowledge.internal.persistence.ConceptEvidenceRepository
import de.optadata.odil.learnwithme.knowledge.internal.persistence.ConceptRepository
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class KnowledgeApiImpl(
    private val conceptRepository: ConceptRepository,
    private val evidenceRepository: ConceptEvidenceRepository,
) : KnowledgeApi {

    override fun listConcepts(sourceId: UUID): List<ConceptSummary> =
        conceptRepository.findAllBySourceIdOrderByFrequencyDesc(sourceId)
            .map { ConceptSummary(it.id, it.name, it.frequency) }

    override fun getConcept(conceptId: UUID): ConceptDetail {
        val concept = conceptRepository.findById(conceptId)
            .orElseThrow { NotFoundException("Konzept $conceptId nicht gefunden") }
        return ConceptDetail(concept.id, concept.workspaceId, concept.sourceId, concept.name, concept.summary)
    }

    override fun listEvidence(conceptId: UUID): List<ConceptEvidenceView> =
        evidenceRepository.findAllByConceptIdOrderByWeightDesc(conceptId).map { ConceptEvidenceView(it.chunkId, it.weight) }
}
