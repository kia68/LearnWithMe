package de.optadata.odil.learnwithme.knowledge.internal.persistence

import de.optadata.odil.learnwithme.knowledge.internal.domain.ConceptEvidence
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConceptEvidenceRepository : JpaRepository<ConceptEvidence, UUID> {
    fun findAllByConceptId(conceptId: UUID): List<ConceptEvidence>
    fun findAllByConceptIdOrderByWeightDesc(conceptId: UUID): List<ConceptEvidence>
}
