package de.optadata.odil.learnwithme.assessment.internal.service

import de.optadata.odil.learnwithme.adaptivity.AdaptivityApi
import de.optadata.odil.learnwithme.adaptivity.ConceptProgressView
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class ProgressOverview(val conceptCount: Int, val averageMastery: Float, val dueCount: Int)

/** D7: Fortschritt pro Konzept. „Verlauf über Zeit" (D7-AC) ist eine bekannte Lücke — dafür
 * bräuchte es eine History-Tabelle/Aggregation über `attempts`, die Epic D nicht baut (siehe
 * docs/progress.md); diese Endpunkte liefern nur den aktuellen Stand. */
@Service
class ProgressService(private val knowledgeApi: KnowledgeApi, private val adaptivityApi: AdaptivityApi) {

    fun overview(workspaceId: UUID, userId: UUID): ProgressOverview {
        val all = adaptivityApi.listAllProgress(workspaceId, userId)
        val due = adaptivityApi.listDue(workspaceId, userId, Instant.now())
        return ProgressOverview(
            conceptCount = all.size,
            averageMastery = if (all.isEmpty()) 0f else all.map { it.mastery }.average().toFloat(),
            dueCount = due.size,
        )
    }

    fun forSource(workspaceId: UUID, userId: UUID, sourceId: UUID): List<ConceptProgressView> {
        val conceptIds = knowledgeApi.listConcepts(sourceId).map { it.id }
        return adaptivityApi.listProgress(workspaceId, userId, conceptIds)
    }

    fun due(workspaceId: UUID, userId: UUID): List<ConceptProgressView> =
        adaptivityApi.listDue(workspaceId, userId, Instant.now())
}
