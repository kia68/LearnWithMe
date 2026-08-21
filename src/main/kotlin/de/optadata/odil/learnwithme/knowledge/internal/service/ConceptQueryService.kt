package de.optadata.odil.learnwithme.knowledge.internal.service

import de.optadata.odil.learnwithme.content.ContentApi
import de.optadata.odil.learnwithme.knowledge.internal.domain.Concept
import de.optadata.odil.learnwithme.knowledge.internal.persistence.ConceptRepository
import org.springframework.stereotype.Service
import java.util.UUID

/** B8: liest Konzepte für die `/sources/{id}/concepts`-Ansicht. Der Ownership-Check läuft
 * über [ContentApi], da `sources` dem `content`-Modul gehört. */
@Service
class ConceptQueryService(
    private val contentApi: ContentApi,
    private val conceptRepository: ConceptRepository,
) {
    fun listForSource(workspaceId: UUID, sourceId: UUID): List<Concept> {
        contentApi.assertOwned(sourceId, workspaceId)
        return conceptRepository.findAllBySourceIdOrderByFrequencyDesc(sourceId)
    }
}
