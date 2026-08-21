package de.optadata.odil.learnwithme.content.internal.service

import de.optadata.odil.learnwithme.content.internal.domain.Section
import de.optadata.odil.learnwithme.content.internal.domain.Source
import de.optadata.odil.learnwithme.content.internal.persistence.SectionRepository
import de.optadata.odil.learnwithme.content.internal.persistence.SourceRepository
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SourceQueryService(
    private val sourceRepository: SourceRepository,
    private val sectionRepository: SectionRepository,
) {

    fun list(workspaceId: UUID, page: Int, size: Int): Page<Source> =
        sourceRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId, PageRequest.of(page, size))

    fun get(workspaceId: UUID, sourceId: UUID): Source =
        sourceRepository.findByIdAndWorkspaceId(sourceId, workspaceId)
            ?: throw NotFoundException("Source $sourceId nicht gefunden")

    fun sections(workspaceId: UUID, sourceId: UUID): List<Section> {
        get(workspaceId, sourceId)
        return sectionRepository.findAllBySourceIdOrderByOrdinal(sourceId)
    }

    /** B6: Bereiche (z.B. Literaturverzeichnis/Anhang) von der weiteren Nutzung ausschließen. */
    @Transactional
    fun excludeSection(workspaceId: UUID, sourceId: UUID, sectionId: UUID, excluded: Boolean): Section {
        get(workspaceId, sourceId)
        val section = sectionRepository.findByIdAndSourceId(sectionId, sourceId)
            ?: throw NotFoundException("Section $sectionId nicht gefunden")
        section.excluded = excluded
        return sectionRepository.save(section)
    }
}
