package de.optadata.odil.learnwithme.content.internal.service

import de.optadata.odil.learnwithme.content.internal.persistence.SourceRepository
import de.optadata.odil.learnwithme.platform.JobQueue
import de.optadata.odil.learnwithme.platform.JobType
import de.optadata.odil.learnwithme.shared.ConflictException
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

/** B5: OCR ist opt-in (Kosten/Zeit sichtbar) — dieser Endpunkt reiht nur den Job ein,
 * die eigentliche OCR läuft im [de.optadata.odil.learnwithme.content.internal.job.OcrJobHandler]. */
@Service
class OcrTriggerService(
    private val sourceRepository: SourceRepository,
    private val jobQueue: JobQueue,
) {

    fun trigger(workspaceId: UUID, sourceId: UUID): UUID {
        val source = sourceRepository.findByIdAndWorkspaceId(sourceId, workspaceId)
            ?: throw NotFoundException("Source $sourceId nicht gefunden")
        if (!source.needsOcr) {
            throw ConflictException("Source $sourceId benötigt kein OCR.")
        }
        return jobQueue.enqueue(
            JobType.OCR,
            "ocr:${source.id}:${System.currentTimeMillis()}",
            mapOf("sourceId" to source.id.toString()),
        )
    }
}
