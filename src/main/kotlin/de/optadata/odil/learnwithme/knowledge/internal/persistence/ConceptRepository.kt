package de.optadata.odil.learnwithme.knowledge.internal.persistence

import de.optadata.odil.learnwithme.knowledge.internal.domain.Concept
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConceptRepository : JpaRepository<Concept, UUID> {
    fun findAllBySourceIdOrderByFrequencyDesc(sourceId: UUID): List<Concept>
}
