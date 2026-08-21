package de.optadata.odil.learnwithme.knowledge.internal.listener

import de.optadata.odil.learnwithme.content.ContentApi
import de.optadata.odil.learnwithme.content.SourceIndexed
import de.optadata.odil.learnwithme.knowledge.internal.domain.Concept
import de.optadata.odil.learnwithme.knowledge.internal.domain.ConceptEvidence
import de.optadata.odil.learnwithme.knowledge.internal.extraction.FrequencyConceptExtractor
import de.optadata.odil.learnwithme.knowledge.internal.persistence.ConceptEvidenceRepository
import de.optadata.odil.learnwithme.knowledge.internal.persistence.ConceptRepository
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/** B8: extrahiert Kernkonzepte, sobald eine Source indiziert ist. Läuft asynchron nach dem
 * `SourceIndexed`-Event (Modulith Event-Publication-Registry = transaktionale Outbox, ADR-002),
 * damit Konzeptextraktion nicht auf dem kritischen Ingestion-Pfad liegt (N2: erste Frage < 60s). */
@Component
class SourceIndexedListener(
    private val contentApi: ContentApi,
    private val conceptRepository: ConceptRepository,
    private val evidenceRepository: ConceptEvidenceRepository,
) {

    @ApplicationModuleListener
    fun on(event: SourceIndexed) {
        val chunks = contentApi.listChunks(event.sourceId)
        if (chunks.isEmpty()) return

        for (extracted in FrequencyConceptExtractor.extract(chunks)) {
            val concept = conceptRepository.save(
                Concept(
                    workspaceId = event.workspaceId,
                    sourceId = event.sourceId,
                    name = extracted.name,
                    summary = extracted.summary,
                    frequency = extracted.frequency,
                ),
            )
            evidenceRepository.saveAll(
                extracted.evidenceChunkIds.map { chunkId -> ConceptEvidence(conceptId = concept.id, chunkId = chunkId) },
            )
        }
    }
}
