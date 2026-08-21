package de.optadata.odil.learnwithme.authoring.internal.service

import de.optadata.odil.learnwithme.authoring.internal.domain.Item
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemStatus
import de.optadata.odil.learnwithme.authoring.internal.persistence.ItemRepository
import de.optadata.odil.learnwithme.shared.ConflictException
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** C7: Dozenten-Review-Queue mit `DRAFT → PUBLISHED | REJECTED` und Bulk-Aktionen. */
@Service
class ItemReviewService(private val itemRepository: ItemRepository) {

    @Transactional
    fun publish(workspaceId: UUID, itemId: UUID): Item = transition(workspaceId, itemId, ItemStatus.PUBLISHED)

    @Transactional
    fun reject(workspaceId: UUID, itemId: UUID): Item = transition(workspaceId, itemId, ItemStatus.REJECTED)

    @Transactional
    fun bulkPublish(workspaceId: UUID, itemIds: List<UUID>): List<Item> = itemIds.map { publish(workspaceId, it) }

    @Transactional
    fun bulkReject(workspaceId: UUID, itemIds: List<UUID>): List<Item> = itemIds.map { reject(workspaceId, it) }

    private fun transition(workspaceId: UUID, itemId: UUID, target: ItemStatus): Item {
        val item = itemRepository.findByIdAndWorkspaceId(itemId, workspaceId)
            ?: throw NotFoundException("Item $itemId nicht gefunden")
        if (item.status != ItemStatus.DRAFT) {
            throw ConflictException("Item $itemId ist im Status ${item.status}, nur DRAFT kann in Review überführt werden.")
        }
        item.status = target
        return itemRepository.save(item)
    }
}
