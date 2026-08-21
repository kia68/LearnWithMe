package de.optadata.odil.learnwithme.authoring.internal.service

import de.optadata.odil.learnwithme.authoring.AuthoringApi
import de.optadata.odil.learnwithme.authoring.CandidateItemView
import de.optadata.odil.learnwithme.authoring.PublishedItemView
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemStatus
import de.optadata.odil.learnwithme.authoring.internal.persistence.ItemRepository
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuthoringApiImpl(private val itemRepository: ItemRepository) : AuthoringApi {

    override fun listPublishedForConcept(workspaceId: UUID, conceptId: UUID): List<CandidateItemView> =
        itemRepository.findAllByConceptIdAndStatus(conceptId, ItemStatus.PUBLISHED)
            .filter { it.workspaceId == workspaceId }
            .map { CandidateItemView(it.id, it.conceptId, it.type.name, it.difficulty, it.difficultyN, it.parentItemId) }

    override fun getPublished(workspaceId: UUID, itemId: UUID): PublishedItemView {
        val item = itemRepository.findByIdAndWorkspaceIdAndStatus(itemId, workspaceId, ItemStatus.PUBLISHED)
            ?: throw NotFoundException("Veröffentlichtes Item $itemId nicht gefunden")
        return PublishedItemView(
            id = item.id,
            conceptId = item.conceptId,
            type = item.type.name,
            stem = item.stem,
            payloadJson = item.payload,
            explanation = item.explanation,
            sourceChunkId = item.sourceChunkId,
            difficulty = item.difficulty,
            difficultyN = item.difficultyN,
        )
    }

    @Transactional
    override fun updateCalibration(itemId: UUID, difficulty: Float, difficultyN: Int, pCorrect: Float?) {
        val item = itemRepository.findById(itemId).orElseThrow { NotFoundException("Item $itemId nicht gefunden") }
        item.difficulty = difficulty
        item.difficultyN = difficultyN
        pCorrect?.let { item.pCorrect = it }
        itemRepository.save(item)
    }

    @Transactional
    override fun recordSkip(itemId: UUID) {
        val item = itemRepository.findById(itemId).orElseThrow { NotFoundException("Item $itemId nicht gefunden") }
        item.skipCount += 1
        itemRepository.save(item)
    }
}
