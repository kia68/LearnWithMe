package de.optadata.odil.learnwithme.content.internal.persistence

import de.optadata.odil.learnwithme.content.internal.domain.Section
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SectionRepository : JpaRepository<Section, UUID> {
    fun findAllBySourceIdOrderByOrdinal(sourceId: UUID): List<Section>

    fun findByIdAndSourceId(id: UUID, sourceId: UUID): Section?

    fun deleteAllBySourceId(sourceId: UUID)
}
