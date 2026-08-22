package de.optadata.odil.learnwithme.authoring.internal.service

import de.optadata.odil.learnwithme.authoring.internal.domain.Item
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemStatus
import de.optadata.odil.learnwithme.authoring.internal.persistence.ItemRepository
import de.optadata.odil.learnwithme.content.ContentApi
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import org.springframework.stereotype.Service
import java.util.UUID

/** M6-Nachtrag (PLAN.md §14 Export): sammelt alle `PUBLISHED`-Items einer Source über deren
 * Konzepte — es gibt keinen direkten Source→Item-Fremdschlüssel (Items hängen an `concepts`,
 * `concepts` an `sources`), N+1-Schleife über `KnowledgeApi.listConcepts` ist hier unproblematisch
 * (Quellen haben typischerweise wenige Konzepte, kein Hot-Path). Neue `authoring → content`-
 * Abhängigkeit (für `assertOwned`) — von `ApplicationModules.verify()` erlaubt, gleiche Art
 * Abweichung von der PLAN.md-§6.3-Tabelle wie schon `assessment` in Epic D. */
@Service
class ItemExportService(
    private val contentApi: ContentApi,
    private val knowledgeApi: KnowledgeApi,
    private val itemRepository: ItemRepository,
) {
    fun listPublishedForSource(workspaceId: UUID, sourceId: UUID): List<Item> {
        contentApi.assertOwned(sourceId, workspaceId)
        return knowledgeApi.listConcepts(sourceId).flatMap { itemRepository.findAllByConceptIdAndStatus(it.id, ItemStatus.PUBLISHED) }
    }
}
