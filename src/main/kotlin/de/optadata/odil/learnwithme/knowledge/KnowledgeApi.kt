package de.optadata.odil.learnwithme.knowledge

import java.util.UUID

data class ConceptSummary(val id: UUID, val name: String, val frequency: Int)

data class ConceptDetail(val id: UUID, val workspaceId: UUID, val sourceId: UUID?, val name: String, val summary: String)

data class ConceptEvidenceView(val chunkId: UUID, val weight: Float)

/** Öffentlicher Port des `knowledge`-Moduls — für `authoring` (Epic C, Fragegenerierung je
 * Konzept mit Beleg, ADR-008). */
interface KnowledgeApi {
    fun listConcepts(sourceId: UUID): List<ConceptSummary>

    /** Wirft `NotFoundException`, wenn das Konzept nicht existiert. */
    fun getConcept(conceptId: UUID): ConceptDetail

    /** Belegstellen absteigend nach Gewicht — Basis für die Chunk-Auswahl bei der Generierung (C1). */
    fun listEvidence(conceptId: UUID): List<ConceptEvidenceView>
}
