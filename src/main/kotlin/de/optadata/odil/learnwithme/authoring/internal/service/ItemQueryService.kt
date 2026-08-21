package de.optadata.odil.learnwithme.authoring.internal.service

import de.optadata.odil.learnwithme.authoring.internal.domain.Item
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemStatus
import de.optadata.odil.learnwithme.authoring.internal.persistence.ItemRepository
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ItemQueryService(
    private val itemRepository: ItemRepository,
    private val knowledgeApi: KnowledgeApi,
) {

    fun listForConcept(workspaceId: UUID, conceptId: UUID): List<Item> {
        val concept = knowledgeApi.getConcept(conceptId)
        if (concept.workspaceId != workspaceId) throw NotFoundException("Konzept $conceptId nicht gefunden")
        return itemRepository.findAllByConceptIdOrderByCreatedAtDesc(conceptId)
    }

    /** C7: Review-Queue — standardmäßig die DRAFT-Warteschlange. */
    fun reviewQueue(workspaceId: UUID, status: ItemStatus, page: Int, size: Int): Page<Item> =
        itemRepository.findAllByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, status, PageRequest.of(page, size))

    fun get(workspaceId: UUID, itemId: UUID): Item =
        itemRepository.findByIdAndWorkspaceId(itemId, workspaceId)
            ?: throw NotFoundException("Item $itemId nicht gefunden")
}
