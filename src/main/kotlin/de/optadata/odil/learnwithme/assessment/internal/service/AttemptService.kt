package de.optadata.odil.learnwithme.assessment.internal.service

import com.fasterxml.jackson.databind.JsonNode
import de.optadata.odil.learnwithme.adaptivity.AdaptivityApi
import de.optadata.odil.learnwithme.adaptivity.RecordAttemptResult
import de.optadata.odil.learnwithme.analytics.AnalyticsApi
import de.optadata.odil.learnwithme.analytics.ErrorAnalysisView
import de.optadata.odil.learnwithme.assessment.internal.domain.Attempt
import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome
import de.optadata.odil.learnwithme.assessment.internal.grading.GradeResult
import de.optadata.odil.learnwithme.assessment.internal.grading.ResponseGrader
import de.optadata.odil.learnwithme.assessment.internal.persistence.AttemptRepository
import de.optadata.odil.learnwithme.assessment.internal.persistence.SessionRepository
import de.optadata.odil.learnwithme.assessment.internal.selection.ItemSelectionService
import de.optadata.odil.learnwithme.assessment.internal.selection.SelectedItem
import de.optadata.odil.learnwithme.authoring.AuthoringApi
import de.optadata.odil.learnwithme.authoring.PublishedItemView
import de.optadata.odil.learnwithme.content.ChunkView
import de.optadata.odil.learnwithme.content.ContentApi
import de.optadata.odil.learnwithme.shared.ConflictException
import de.optadata.odil.learnwithme.shared.JsonMapper
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AttemptResult(
    val attempt: Attempt,
    val grade: GradeResult,
    val item: PublishedItemView,
    val evidenceChunk: ChunkView?,
    val adaptResult: RecordAttemptResult,
    val next: SelectedItem?,
    val errorAnalysis: ErrorAnalysisView?,
)

data class SkipResult(val next: SelectedItem?)

/**
 * Der kritische Pfad (§6.5, N1: p95 < 400 ms, kein LLM-Call). D2-D4/D6: Grading → Attempt
 * persistieren (append-only) → Elo+FSRS-Update → Item-Kalibrierung zurückschreiben → nächstes Item.
 */
@Service
class AttemptService(
    private val sessionRepository: SessionRepository,
    private val attemptRepository: AttemptRepository,
    private val authoringApi: AuthoringApi,
    private val adaptivityApi: AdaptivityApi,
    private val contentApi: ContentApi,
    private val grader: ResponseGrader,
    private val selectionService: ItemSelectionService,
    private val analyticsApi: AnalyticsApi,
) {
    private val mapper = JsonMapper.instance

    @Transactional
    fun submit(workspaceId: UUID, userId: UUID, sessionId: UUID, itemId: UUID, response: JsonNode, elapsedMs: Int): AttemptResult {
        val session = sessionRepository.findByIdAndWorkspaceId(sessionId, workspaceId)
            ?: throw NotFoundException("Session $sessionId nicht gefunden")
        if (session.userId != userId) throw NotFoundException("Session $sessionId nicht gefunden")
        if (session.endedAt != null) throw ConflictException("Session $sessionId ist bereits beendet")

        val item = authoringApi.getPublished(workspaceId, itemId)
        val grade = grader.grade(item.type, item.payloadJson, response)
        val now = Instant.now()

        val adaptResult = adaptivityApi.recordAttempt(
            workspaceId, userId, item.conceptId, item.difficulty, item.difficultyN, grade.score, now,
        )
        authoringApi.updateCalibration(itemId, adaptResult.newItemDifficulty, adaptResult.newItemDifficultyN, pCorrect = null)

        val attempt = attemptRepository.save(
            Attempt(
                workspaceId = workspaceId,
                sessionId = sessionId,
                userId = userId,
                itemId = itemId,
                itemType = item.type,
                conceptId = item.conceptId,
                response = mapper.writeValueAsString(response),
                outcome = grade.outcome,
                score = grade.score,
                elapsedMs = elapsedMs,
                thetaBefore = adaptResult.thetaBefore,
                thetaAfter = adaptResult.thetaAfter,
                pExpected = adaptResult.pExpected,
                createdAt = now,
            ),
        )

        // E1/E2/E3/E6: nur bei falscher/teilweise falscher Antwort — synchron, kein LLM (N1).
        val errorAnalysis = if (grade.outcome != AttemptOutcome.CORRECT) {
            analyticsApi.analyzeError(
                workspaceId = workspaceId,
                userId = userId,
                attemptId = attempt.id,
                itemId = itemId,
                conceptId = item.conceptId,
                itemType = item.type,
                expectedSuccess = adaptResult.pExpected,
                elapsedMs = elapsedMs,
                thetaBefore = adaptResult.thetaBefore,
                itemDifficulty = item.difficulty,
                chosenOptionMisconceptionCategory = grade.chosenOptionMisconceptionCategory,
            )
        } else {
            null
        }

        val evidenceChunk = contentApi.getChunk(item.sourceChunkId)
        // E2: bei einem Fehler gezielt dasselbe Konzept nachfragen, bevorzugt als Paraphrase (E6).
        val next = if (errorAnalysis != null) {
            selectionService.selectNext(session, now, preferredConceptId = item.conceptId, preferParaphraseOfItemId = itemId)
        } else {
            selectionService.selectNext(session, now)
        }
        return AttemptResult(attempt, grade, item, evidenceChunk, adaptResult, next, errorAnalysis)
    }

    /** D6: Skip zählt nicht als Fehler (θ unverändert), fließt aber als Item-Qualitätssignal ein. */
    @Transactional
    fun skip(workspaceId: UUID, userId: UUID, sessionId: UUID, itemId: UUID, reason: String?): SkipResult {
        val session = sessionRepository.findByIdAndWorkspaceId(sessionId, workspaceId)
            ?: throw NotFoundException("Session $sessionId nicht gefunden")
        if (session.userId != userId) throw NotFoundException("Session $sessionId nicht gefunden")

        val item = authoringApi.getPublished(workspaceId, itemId)
        authoringApi.recordSkip(itemId)
        val progress = adaptivityApi.getProgress(workspaceId, userId, item.conceptId)

        attemptRepository.save(
            Attempt(
                workspaceId = workspaceId,
                sessionId = sessionId,
                userId = userId,
                itemId = itemId,
                itemType = item.type,
                conceptId = item.conceptId,
                response = mapper.writeValueAsString(mapOf("skipped" to true, "reason" to reason)),
                outcome = AttemptOutcome.SKIPPED,
                score = 0f,
                elapsedMs = 0,
                thetaBefore = progress.theta,
                thetaAfter = progress.theta,
                pExpected = adaptivityApi.expectedSuccess(progress.theta, item.difficulty),
            ),
        )

        return SkipResult(selectionService.selectNext(session))
    }
}
