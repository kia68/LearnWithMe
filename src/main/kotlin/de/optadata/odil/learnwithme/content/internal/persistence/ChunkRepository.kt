package de.optadata.odil.learnwithme.content.internal.persistence

import de.optadata.odil.learnwithme.content.internal.domain.Chunk
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChunkRepository : JpaRepository<Chunk, UUID> {
    fun findAllBySourceIdOrderByOrdinal(sourceId: UUID): List<Chunk>

    fun deleteAllBySourceId(sourceId: UUID)
}
