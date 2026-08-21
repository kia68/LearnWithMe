package de.optadata.odil.learnwithme.adaptivity.internal.service

import de.optadata.odil.learnwithme.adaptivity.AdaptivityApi
import de.optadata.odil.learnwithme.adaptivity.ConceptProgressView
import de.optadata.odil.learnwithme.adaptivity.RecordAttemptResult
import de.optadata.odil.learnwithme.adaptivity.internal.config.AdaptivityProperties
import de.optadata.odil.learnwithme.adaptivity.internal.domain.LearnerConceptState
import de.optadata.odil.learnwithme.adaptivity.internal.domain.LearnerConceptStateId
import de.optadata.odil.learnwithme.adaptivity.internal.domain.LearnerState
import de.optadata.odil.learnwithme.adaptivity.internal.engine.EloEngine
import de.optadata.odil.learnwithme.adaptivity.internal.engine.FsrsEngine
import de.optadata.odil.learnwithme.adaptivity.internal.persistence.LearnerConceptStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Zwei getrennte Modelle in einem Update-Schritt (§11.1): Elo für „wie schwer darf die nächste
 * Frage sein", FSRS für „wann muss ich das wiedersehen". Kein LLM-Call, keine `authoring`-Abhängigkeit
 * (§6.3) — Item-Schwierigkeit kommt als Parameter herein und geht als Ergebniswert wieder heraus;
 * `assessment` schreibt sie über `authoring.AuthoringApi.updateCalibration` zurück. */
@Service
class AdaptivityApiImpl(
    private val repository: LearnerConceptStateRepository,
    private val properties: AdaptivityProperties,
) : AdaptivityApi {

    override fun expectedSuccess(theta: Float, itemDifficulty: Float): Float =
        EloEngine.successProbability(theta, itemDifficulty)

    override fun getProgress(workspaceId: UUID, userId: UUID, conceptId: UUID): ConceptProgressView =
        repository.findById(LearnerConceptStateId(userId, conceptId))
            .filter { it.workspaceId == workspaceId }
            .map { it.toView() }
            .orElseGet { defaultView(conceptId) }

    override fun listProgress(workspaceId: UUID, userId: UUID, conceptIds: List<UUID>): List<ConceptProgressView> {
        if (conceptIds.isEmpty()) return emptyList()
        val states = repository.findAllByWorkspaceIdAndIdUserIdAndIdConceptIdIn(workspaceId, userId, conceptIds)
            .associateBy { it.conceptId }
        return conceptIds.map { states[it]?.toView() ?: defaultView(it) }
    }

    override fun listAllProgress(workspaceId: UUID, userId: UUID): List<ConceptProgressView> =
        repository.findAllByWorkspaceIdAndIdUserId(workspaceId, userId).map { it.toView() }

    override fun listDue(workspaceId: UUID, userId: UUID, now: Instant): List<ConceptProgressView> =
        repository.findDue(workspaceId, userId, now).map { it.toView() }

    @Transactional
    override fun recordAttempt(
        workspaceId: UUID,
        userId: UUID,
        conceptId: UUID,
        itemDifficulty: Float,
        itemDifficultyN: Int,
        score: Float,
        now: Instant,
    ): RecordAttemptResult {
        val id = LearnerConceptStateId(userId, conceptId)
        val state = repository.findById(id).orElseGet { LearnerConceptState(id, workspaceId) }
        val thetaBeforeValue = state.theta

        val elo = EloEngine.update(
            thetaBefore = state.theta,
            thetaN = state.thetaN,
            itemDifficultyBefore = itemDifficulty,
            itemDifficultyN = itemDifficultyN,
            score = score,
            userKA = properties.elo.userKA,
            userKB = properties.elo.userKB,
            itemKA = properties.elo.itemKA,
            itemKB = properties.elo.itemKB,
        )

        val fsrs = FsrsEngine.review(
            stability = state.fsrsStability,
            difficulty = state.fsrsDifficulty,
            state = state.state,
            reps = state.reps,
            lapses = state.lapses,
            lastReviewAt = state.lastReviewAt,
            grade = FsrsEngine.gradeFromScore(score),
            targetRetention = properties.fsrs.targetRetention,
            now = now,
        )

        state.theta = elo.thetaAfter
        state.thetaN = elo.thetaN
        state.mastery = EloEngine.successProbability(elo.thetaAfter, 0f)
        state.fsrsStability = fsrs.stability
        state.fsrsDifficulty = fsrs.difficulty
        state.lastReviewAt = now
        state.dueAt = fsrs.dueAt
        state.reps = fsrs.reps
        state.lapses = fsrs.lapses
        state.state = fsrs.state
        repository.save(state)

        return RecordAttemptResult(
            thetaBefore = thetaBeforeValue,
            thetaAfter = elo.thetaAfter,
            pExpected = elo.pExpected,
            mastery = state.mastery,
            newItemDifficulty = elo.itemDifficultyAfter,
            newItemDifficultyN = elo.itemDifficultyN,
            dueAt = fsrs.dueAt,
        )
    }

    private fun defaultView(conceptId: UUID) = ConceptProgressView(
        conceptId = conceptId,
        theta = 0f,
        mastery = EloEngine.successProbability(0f, 0f),
        state = LearnerState.NEW.name,
        reps = 0,
        lapses = 0,
        dueAt = null,
    )

    private fun LearnerConceptState.toView() = ConceptProgressView(
        conceptId = conceptId,
        theta = theta,
        mastery = mastery,
        state = state.name,
        reps = reps,
        lapses = lapses,
        dueAt = dueAt,
    )
}
