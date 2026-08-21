package de.optadata.odil.learnwithme.knowledge

import java.util.UUID

data class ConceptSummary(val id: UUID, val name: String, val frequency: Int)

/** Öffentlicher Port des `knowledge`-Moduls — u.a. für `authoring` (Epic C, Fragegenerierung
 * je Konzept), noch ungenutzt außerhalb dieses Moduls. */
interface KnowledgeApi {
    fun listConcepts(sourceId: UUID): List<ConceptSummary>
}
