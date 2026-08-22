package de.optadata.odil.learnwithme.assessment.internal.service

import de.optadata.odil.learnwithme.adaptivity.AdaptivityApi
import de.optadata.odil.learnwithme.adaptivity.RecordAttemptResult
import de.optadata.odil.learnwithme.analytics.AnalyticsApi
import de.optadata.odil.learnwithme.analytics.ErrorAnalysisView
import de.optadata.odil.learnwithme.assessment.internal.domain.Attempt
import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome
import de.optadata.odil.learnwithme.assessment.internal.grading.FreeTextGrade
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
import de.optadata.odil.learnwithme.platform.JobOutcome
import de.optadata.odil.learnwithme.platform.JobQueue
import de.optadata.odil.learnwithme.platform.JobType
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

/** Epic H: Ergebnis von [AttemptService.gradeStatus] — Poll-Ziel für den asynchronen
 * `SHORT_ANSWER`-Pfad. [Graded.result] hat `next = null`/`errorAnalysis = null`: der Nutzer hat
 * sein nächstes Item schon beim ursprünglichen `POST attempts` bekommen, und die
 * Fehlerklassifikation NICHT hier erneut auszulösen ist wichtig — [de.optadata.odil.learnwithme.analytics.AnalyticsApi.analyzeError]
 * pflegt bei jedem Aufruf die `misconceptions`-Tabelle (E3) und würde beim wiederholten Pollen sonst doppelt zählen. */
sealed interface GradeStatus {
    data object Pending : GradeStatus
    data class Graded(val result: AttemptResult) : GradeStatus

    /** Härtung: `GRADE_FREE_TEXT` scheiterte nach `JobWorker.MAX_ATTEMPTS` Versuchen endgültig
     * (vorher blieb der Poll-Endpunkt für immer bei [Pending] stehen, ohne dass der Client das von
     * einem noch laufenden Job unterscheiden konnte — siehe `docs/progress.md` „Bekannte Lücken"
     * Epic H). Die Antwort selbst ist nicht verloren (`jobs.payload` behält sie), nur ungegradet. */
    data class Failed(val lastError: String?) : GradeStatus
}

/** Epic H (E4): `SHORT_ANSWER` braucht einen LLM-Call zum Graden, der kritische Pfad (N1) darf
 * aber keinen haben — die Antwort auf `POST attempts` ist deshalb sofort da, ohne dass das
 * Ergebnis schon feststeht. [GradeFreeTextJobHandler][de.optadata.odil.learnwithme.assessment.internal.job.GradeFreeTextJobHandler]
 * liefert das echte Ergebnis asynchron nach; `GET .../items/{itemId}/grade` pollt darauf. */
sealed interface SubmitOutcome {
    data class Graded(val result: AttemptResult) : SubmitOutcome
    data object Pending : SubmitOutcome
}

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
    private val jobQueue: JobQueue,
) {
    private val mapper = JsonMapper.instance

    @Transactional
    fun submit(workspaceId: UUID, userId: UUID, sessionId: UUID, itemId: UUID, response: Any, elapsedMs: Int): SubmitOutcome {
        val session = sessionRepository.findByIdAndWorkspaceId(sessionId, workspaceId)
            ?: throw NotFoundException("Session $sessionId nicht gefunden")
        if (session.userId != userId) throw NotFoundException("Session $sessionId nicht gefunden")
        if (session.endedAt != null) throw ConflictException("Session $sessionId ist bereits beendet")

        val item = authoringApi.getPublished(workspaceId, itemId)

        if (item.type == "SHORT_ANSWER") {
            jobQueue.enqueue(
                JobType.GRADE_FREE_TEXT,
                "${gradeJobKeyPrefix(sessionId, itemId)}${System.currentTimeMillis()}",
                workspaceId,
                mapOf(
                    "workspaceId" to workspaceId.toString(),
                    "userId" to userId.toString(),
                    "sessionId" to sessionId.toString(),
                    "itemId" to itemId.toString(),
                    "response" to mapper.writeValueAsString(response),
                    "elapsedMs" to elapsedMs,
                ),
            )
            return SubmitOutcome.Pending
        }

        // `response` kommt vom Controller als generisches Any (Map/List/Primitiv) — s. SessionDtos.kt-
        // Kommentar zur Jackson-2/3-Falle; das Grading selbst arbeitet weiterhin mit JsonNode (§10.2).
        val grade = grader.grade(item.type, item.payloadJson, mapper.valueToTree(response))
        val now = Instant.now()
        return SubmitOutcome.Graded(finalize(workspaceId, userId, sessionId, itemId, item, response, elapsedMs, grade, now, selectNext = true))
    }

    /** Epic H: Gegenstück zu [submit] für den asynchronen `SHORT_ANSWER`-Pfad — aufgerufen vom
     * `GradeFreeTextJobHandler`, nachdem [de.optadata.odil.learnwithme.assessment.internal.grading.FreeTextGrader]
     * das LLM-Rubric-Ergebnis geliefert hat. Kein `next`: der Nutzer hat beim `POST attempts` schon
     * optimistisch das nächste Item bekommen (`SessionController`/`peekNext`), hier geht es nur noch
     * darum, diesen einen Attempt nachträglich vollständig (Elo/FSRS/Fehleranalyse) zu verbuchen. */
    @Transactional
    fun finalizeShortAnswerGrade(
        workspaceId: UUID,
        userId: UUID,
        sessionId: UUID,
        itemId: UUID,
        responseJson: String,
        elapsedMs: Int,
        freeTextGrade: FreeTextGrade,
    ): AttemptResult {
        val item = authoringApi.getPublished(workspaceId, itemId)
        val response = mapper.readValue(responseJson, Any::class.java)
        val grade = GradeResult(
            score = freeTextGrade.score,
            outcome = freeTextGrade.outcome,
            correctResponseJson = mapper.writeValueAsString(mapOf("answer" to freeTextReferenceAnswer(item))),
            chosenOptionRationale = freeTextGrade.feedback,
            chosenOptionMisconceptionCategory = null,
        )
        return finalize(workspaceId, userId, sessionId, itemId, item, response, elapsedMs, grade, Instant.now(), selectNext = false)
    }

    private fun freeTextReferenceAnswer(item: PublishedItemView): String =
        mapper.readTree(item.payloadJson).path("referenceAnswer").asText("")

    private fun gradeJobKeyPrefix(sessionId: UUID, itemId: UUID): String = "grade:$sessionId:$itemId:"

    private fun finalize(
        workspaceId: UUID,
        userId: UUID,
        sessionId: UUID,
        itemId: UUID,
        item: PublishedItemView,
        response: Any,
        elapsedMs: Int,
        grade: GradeResult,
        now: Instant,
        selectNext: Boolean,
    ): AttemptResult {
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
                feedback = if (item.type == "SHORT_ANSWER") grade.chosenOptionRationale else null,
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
        val next = if (!selectNext) {
            null
        } else if (errorAnalysis != null) {
            // E2: bei einem Fehler gezielt dasselbe Konzept nachfragen, bevorzugt als Paraphrase (E6).
            selectionService.selectNext(session(workspaceId, sessionId), now, preferredConceptId = item.conceptId, preferParaphraseOfItemId = itemId)
        } else {
            selectionService.selectNext(session(workspaceId, sessionId), now)
        }
        return AttemptResult(attempt, grade, item, evidenceChunk, adaptResult, next, errorAnalysis)
    }

    private fun session(workspaceId: UUID, sessionId: UUID) =
        sessionRepository.findByIdAndWorkspaceId(sessionId, workspaceId) ?: throw NotFoundException("Session $sessionId nicht gefunden")

    /** Epic H: Poll-Ziel für den asynchronen `SHORT_ANSWER`-Grade — [GradeStatus.Pending], solange
     * [finalizeShortAnswerGrade] noch nicht gelaufen ist. `mastery`/`dueAt` kommen frisch aus
     * [AdaptivityApi.getProgress] statt aus einem gespeicherten Wert — [finalizeShortAnswerGrade]
     * hat sie zum Zeitpunkt des Pollens bereits verbucht, der aktuelle Fortschrittsstand ist
     * also identisch mit dem, was direkt nach dem Grading zurückgekommen wäre. */
    fun gradeStatus(workspaceId: UUID, sessionId: UUID, itemId: UUID): GradeStatus {
        session(workspaceId, sessionId) // wirft NotFoundException, wenn die Session nicht zum Workspace gehört
        val attempt = attemptRepository.findFirstBySessionIdAndItemIdOrderByCreatedAtDesc(sessionId, itemId) ?: run {
            val jobStatus = jobQueue.statusByKeyPrefix(workspaceId, gradeJobKeyPrefix(sessionId, itemId))
            return if (jobStatus?.outcome == JobOutcome.FAILED) GradeStatus.Failed(jobStatus.lastError) else GradeStatus.Pending
        }

        val item = authoringApi.getPublished(workspaceId, itemId)
        val evidenceChunk = contentApi.getChunk(item.sourceChunkId)
        val progress = adaptivityApi.getProgress(workspaceId, attempt.userId, item.conceptId)
        val grade = GradeResult(
            score = attempt.score,
            outcome = attempt.outcome,
            correctResponseJson = mapper.writeValueAsString(mapOf("answer" to freeTextReferenceAnswer(item))),
            chosenOptionRationale = attempt.feedback,
            chosenOptionMisconceptionCategory = null,
        )
        val adaptResult = RecordAttemptResult(
            thetaBefore = attempt.thetaBefore,
            thetaAfter = attempt.thetaAfter,
            pExpected = attempt.pExpected,
            mastery = progress.mastery,
            newItemDifficulty = item.difficulty,
            newItemDifficultyN = item.difficultyN,
            dueAt = progress.dueAt ?: attempt.createdAt,
        )
        return GradeStatus.Graded(AttemptResult(attempt, grade, item, evidenceChunk, adaptResult, next = null, errorAnalysis = null))
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
