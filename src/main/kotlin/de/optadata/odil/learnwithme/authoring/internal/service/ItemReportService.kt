package de.optadata.odil.learnwithme.authoring.internal.service

import de.optadata.odil.learnwithme.authoring.internal.domain.ItemReport
import de.optadata.odil.learnwithme.authoring.internal.persistence.ItemReportRepository
import de.optadata.odil.learnwithme.authoring.internal.persistence.ItemRepository
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * C5: 1-Klick-Report. Speichert Grund + erhöht `report_count` — die AC „wird sofort aus meiner
 * Rotation genommen" ist eine PRO-NUTZER-Filterung, die es erst mit dem Session-/Auswahl-Modul
 * (Epic D, `assessment`) geben kann; ohne dieses Modul gibt es keine Rotation, aus der entfernt
 * werden könnte. Der Report selbst ist vollständig persistiert, damit Epic D ihn später auswerten
 * kann (siehe docs/progress.md).
 */
@Service
class ItemReportService(
    private val itemRepository: ItemRepository,
    private val reportRepository: ItemReportRepository,
) {

    @Transactional
    fun report(workspaceId: UUID, userId: UUID, itemId: UUID, reason: String, comment: String?) {
        val item = itemRepository.findByIdAndWorkspaceId(itemId, workspaceId)
            ?: throw NotFoundException("Item $itemId nicht gefunden")

        reportRepository.save(ItemReport(itemId = itemId, userId = userId, reason = reason, comment = comment))
        item.reportCount += 1
        itemRepository.save(item)
    }
}
