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
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/** Ein neues, unkalibriertes Item wird bevorzugt kalibriert (§11.3 Schritt 3: `explore(i)`). */
private const val EXPLORATION_WEIGHT = 0.05f

/**
 * Item-Auswahl-Policy (§11.3, D2/D9, E2/E6). Bewusst vereinfacht gegenüber PLAN.md:
 * - Kein Prerequisite-Graph-Filter — `knowledge.concept_relations` (PLAN §8.1) wird von keinem
 *   Modul befüllt (Epic B extrahiert nur Frequenz-Konzepte, keine Relationen); ein Filter ohne
 *   Datenquelle wäre toter Code. Siehe docs/progress.md.
 * - `MIXED`-Scope vereinfacht auf „fällige Konzepte" — die volle Drei-Wege-Gewichtung
 *   (fällig/Sessionziel/schwächste Konzepte) bräuchte eine workspace-weite Konzeptliste ohne
 *   Source-Filter, die es in `KnowledgeApi` nicht gibt (nur `listConcepts(sourceId)`).
 */
@Component
class ItemSelectionService(
    private val authoringApi: AuthoringApi,
    private val knowledgeApi: KnowledgeApi,
    private val adaptivityApi: AdaptivityApi,
    private val attemptRepository: AttemptRepository,
    private val properties: SelectionProperties,
) {

    /**
     * [preferredConceptId] (E2): wenn gesetzt und das Konzept auswählbare Items hat, wird
     * AUSSCHLIESSLICH aus diesem Konzept gewählt statt aus dem üblichen Scope-Pool — die
     * Nachfrage nach einem Fehler prüft gezielt dieselbe Quellstelle, nicht irgendeine.
     * [preferParaphraseOfItemId] (E6): innerhalb dieses eingeschränkten Pools wird eine
     * Paraphrase-Variante (`parentItemId == preferParaphraseOfItemId`) direkt gewählt, falls
     * vorhanden — statt wörtlicher Wiederholung.
     */
    fun selectNext(
        session: Session,
        now: Instant = Instant.now(),
        preferredConceptId: UUID? = null,
        preferParaphraseOfItemId: UUID? = null,
    ): SelectedItem? {
        val conceptIds = resolveConceptPool(session, now, preferredConceptId)
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

        if (preferParaphraseOfItemId != null) {
            val paraphrase = allCandidates.firstOrNull { it.parentItemId == preferParaphraseOfItemId && it.id !in recentItemIds }
            if (paraphrase != null) return toSelectedItem(session.workspaceId, paraphrase, theta[paraphrase.conceptId] ?: 0f)
        }

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
        return toSelectedItem(session.workspaceId, chosen, theta[chosen.conceptId] ?: 0f)
    }

    private fun toSelectedItem(workspaceId: UUID, candidate: CandidateItemView, theta: Float): SelectedItem {
        val detail = authoringApi.getPublished(workspaceId, candidate.id)
        return SelectedItem(
            itemId = detail.id,
            conceptId = detail.conceptId,
            type = detail.type,
            stem = detail.stem,
            payloadJson = detail.payloadJson,
            expectedSuccess = adaptivityApi.expectedSuccess(theta, detail.difficulty),
        )
    }

    /** [preferredConceptId] (E2) gewinnt, wenn es veröffentlichte Items hat — sonst der übliche
     * Scope-Pool. */
    private fun resolveConceptPool(session: Session, now: Instant, preferredConceptId: UUID?): List<UUID> {
        if (preferredConceptId != null && authoringApi.listPublishedForConcept(session.workspaceId, preferredConceptId).isNotEmpty()) {
            return listOf(preferredConceptId)
        }
        return when (session.scopeKind) {
            SessionScopeKind.CONCEPT -> listOfNotNull(session.scopeId)
            SessionScopeKind.SOURCE -> session.scopeId?.let { knowledgeApi.listConcepts(it).map { c -> c.id } } ?: emptyList()
            SessionScopeKind.DUE_REVIEW, SessionScopeKind.MIXED ->
                adaptivityApi.listDue(session.workspaceId, session.userId, now).map { it.conceptId }
        }
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
