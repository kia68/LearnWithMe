package de.optadata.odil.learnwithme.content.internal.service

import de.optadata.odil.learnwithme.content.ChunkView
import de.optadata.odil.learnwithme.content.ContentApi
import de.optadata.odil.learnwithme.content.internal.persistence.ChunkRepository
import de.optadata.odil.learnwithme.content.internal.persistence.SourceRepository
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ContentApiImpl(
    private val sourceRepository: SourceRepository,
    private val chunkRepository: ChunkRepository,
) : ContentApi {

    override fun assertOwned(sourceId: UUID, workspaceId: UUID) {
        sourceRepository.findByIdAndWorkspaceId(sourceId, workspaceId)
            ?: throw NotFoundException("Source $sourceId nicht gefunden")
    }

    override fun listChunks(sourceId: UUID): List<ChunkView> =
        chunkRepository.findAllBySourceIdOrderByOrdinal(sourceId).map { ChunkView(it.id, it.ordinal, it.text) }
}
