package de.optadata.odil.learnwithme.assessment.internal.selection

import de.optadata.odil.learnwithme.adaptivity.AdaptivityApi
import de.optadata.odil.learnwithme.assessment.internal.config.SelectionProperties
import de.optadata.odil.learnwithme.assessment.internal.domain.Session
import de.optadata.odil.learnwithme.assessment.internal.domain.SessionScopeKind
import de.optadata.odil.learnwithme.assessment.internal.persistence.AttemptRepository
import de.optadata.odil.learnwithme.authoring.AuthoringApi
import de.optadata.odil.learnwithme.authoring.CandidateItemView
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/** Ein neues, unkalibriertes Item wird bevorzugt kalibriert (§11.3 Schritt 3: `explore(i)`). */
private const val EXPLORATION_WEIGHT = 0.05f

/**
 * Item-Auswahl-Policy (§11.3, D2/D9). Bewusst vereinfacht gegenüber PLAN.md:
 * - Kein Prerequisite-Graph-Filter — `knowledge.concept_relations` (PLAN §8.1) wird von keinem
 *   Modul befüllt (Epic B extrahiert nur Frequenz-Konzepte, keine Relationen); ein Filter ohne
 *   Datenquelle wäre toter Code. Siehe docs/progress.md.
 * - `MIXED`-Scope vereinfacht auf „fällige Konzepte" — die volle Drei-Wege-Gewichtung
 *   (fällig/Sessionziel/schwächste Konzepte) bräuchte eine workspace-weite Konzeptliste ohne
 *   Source-Filter, die es in `KnowledgeApi` nicht gibt (nur `listConcepts(sourceId)`).
 * - Paraphrase-Bevorzugung bei zuletzt falscher Antwort (E6) entfällt: Epic C generiert keine
 *   Paraphrase-Varianten (`parent_item_id` bleibt ungenutzt), das ist nicht Teil von Epic D.
 */
@Component
class ItemSelectionService(
    private val authoringApi: AuthoringApi,
    private val knowledgeApi: KnowledgeApi,
    private val adaptivityApi: AdaptivityApi,
    private val attemptRepository: AttemptRepository,
    private val properties: SelectionProperties,
) {

    fun selectNext(session: Session, now: Instant = Instant.now()): SelectedItem? {
        val conceptIds = resolveConceptPool(session, now)
        if (conceptIds.isEmpty()) return null

        val theta = adaptivityApi.listProgress(session.workspaceId, session.userId, conceptIds)
            .associate { it.conceptId to it.theta }

        val recent = attemptRepository.findAllBySessionIdOrderByCreatedAtDesc(
            session.id,
            PageRequest.of(0, properties.selection.recentItemWindow),
        )
        val recentItemIds = recent.map { it.itemId }.toSet()
        val avoidType = typeToAvoid(recent.map { it.itemType }, properties.selection.maxSameTypeInARow)

        val allCandidates = conceptIds.flatMap { authoringApi.listPublishedForConcept(session.workspaceId, it) }
        var pool = allCandidates.filterNot { it.id in recentItemIds }
        if (avoidType != null) {
            val withoutAvoidType = pool.filterNot { it.type == avoidType }
            if (withoutAvoidType.isNotEmpty()) pool = withoutAvoidType
        }
        if (pool.isEmpty()) pool = allCandidates
        if (pool.isEmpty()) return null

        val scored = pool.map { candidate ->
            val candidateTheta = theta[candidate.conceptId] ?: 0f
            val expected = adaptivityApi.expectedSuccess(candidateTheta, candidate.difficulty)
            val explore = EXPLORATION_WEIGHT / (1 + candidate.difficultyN)
            candidate to (-abs(expected - properties.targetSuccessProbability) + explore)
        }

        val chosen = softmaxPick(scored, properties.selection.softmaxTemperature)
        val chosenTheta = theta[chosen.conceptId] ?: 0f
        val detail = authoringApi.getPublished(session.workspaceId, chosen.id)
        return SelectedItem(
            itemId = detail.id,
            conceptId = detail.conceptId,
            type = detail.type,
            stem = detail.stem,
            payloadJson = detail.payloadJson,
            expectedSuccess = adaptivityApi.expectedSuccess(chosenTheta, detail.difficulty),
        )
    }

    private fun resolveConceptPool(session: Session, now: Instant) = when (session.scopeKind) {
        SessionScopeKind.CONCEPT -> listOfNotNull(session.scopeId)
        SessionScopeKind.SOURCE -> session.scopeId?.let { knowledgeApi.listConcepts(it).map { c -> c.id } } ?: emptyList()
        SessionScopeKind.DUE_REVIEW, SessionScopeKind.MIXED ->
            adaptivityApi.listDue(session.workspaceId, session.userId, now).map { it.conceptId }
    }

    /** Erzwingt die Typ-Rotation (D9): nicht mehr als [maxSameTypeInARow] gleiche Typen in Folge. */
    private fun typeToAvoid(recentTypesNewestFirst: List<String>, maxSameTypeInARow: Int): String? {
        if (maxSameTypeInARow <= 0 || recentTypesNewestFirst.size < maxSameTypeInARow) return null
        val streak = recentTypesNewestFirst.take(maxSameTypeInARow).distinct()
        return streak.singleOrNull()
    }

    private fun softmaxPick(scored: List<Pair<CandidateItemView, Float>>, temperature: Float): CandidateItemView {
        val top = scored.sortedByDescending { it.second }.take(5)
        if (top.size == 1) return top.single().first
        val tau = if (temperature <= 0f) 0.01f else temperature
        val scaled = top.map { it.second / tau }
        val max = scaled.max()
        val weights = scaled.map { exp((it - max).toDouble()) }
        val total = weights.sum()
        val threshold = Random.nextDouble() * total
        var cumulative = 0.0
        for (i in top.indices) {
            cumulative += weights[i]
            if (threshold <= cumulative) return top[i].first
        }
        return top.last().first
    }
}
