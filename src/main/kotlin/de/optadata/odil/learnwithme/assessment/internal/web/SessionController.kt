package de.optadata.odil.learnwithme.assessment.internal.web

import de.optadata.odil.learnwithme.assessment.internal.domain.Session
import de.optadata.odil.learnwithme.assessment.internal.domain.SessionGoalKind
import de.optadata.odil.learnwithme.assessment.internal.domain.SessionScopeKind
import de.optadata.odil.learnwithme.assessment.internal.selection.SelectedItem
import de.optadata.odil.learnwithme.assessment.internal.service.AttemptResult
import de.optadata.odil.learnwithme.assessment.internal.service.AttemptService
import de.optadata.odil.learnwithme.assessment.internal.service.SessionService
import de.optadata.odil.learnwithme.assessment.internal.service.SessionSummary
import de.optadata.odil.learnwithme.assessment.internal.web.dto.EvidenceResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.FeedbackResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.FinishSessionResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.ItemMeta
import de.optadata.odil.learnwithme.assessment.internal.web.dto.LearnerUpdateResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.NextItemResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SessionResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SkipRequest
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SkipResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.StartSessionRequest
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SubmitAttemptRequest
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SubmitAttemptResponse
import de.optadata.odil.learnwithme.shared.JsonMapper
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** D1-D9: Session-Lebenszyklus, der kritische Antwort-Pfad (§6.5) und Skip (D6). */
@RestController
class SessionController(
    private val sessionService: SessionService,
    private val attemptService: AttemptService,
) {
    private val mapper = JsonMapper.instance

    @PostMapping("/api/v1/sessions")
    fun start(@AuthenticationPrincipal principal: TenantPrincipal, @RequestBody request: StartSessionRequest): SessionResponse {
        val (session, next) = sessionService.start(
            principal.workspaceId,
            principal.userId,
            SessionScopeKind.valueOf(request.scopeKind),
            request.scopeId,
            SessionGoalKind.valueOf(request.goalKind),
            request.goalValue,
        )
        return session.toResponse(next.toResponse())
    }

    @GetMapping("/api/v1/sessions/{id}")
    fun get(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID): SessionResponse =
        sessionService.get(principal.workspaceId, id).toResponse(null)

    /** Prefetch/Offline (D8): liest den nächsten Kandidaten, ohne einen Attempt zu erzeugen. */
    @GetMapping("/api/v1/sessions/{id}/next")
    fun next(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID): NextItemResponse? =
        sessionService.peekNext(principal.workspaceId, id).toResponse()

    @PostMapping("/api/v1/sessions/{id}/attempts")
    fun submitAttempt(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @PathVariable id: UUID,
        @RequestBody request: SubmitAttemptRequest,
    ): SubmitAttemptResponse =
        attemptService.submit(principal.workspaceId, principal.userId, id, request.itemId, request.response, request.elapsedMs).toResponse()

    @PostMapping("/api/v1/sessions/{id}/skip")
    fun skip(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID, @RequestBody request: SkipRequest): SkipResponse =
        SkipResponse(attemptService.skip(principal.workspaceId, principal.userId, id, request.itemId, request.reason).next.toResponse())

    @PostMapping("/api/v1/sessions/{id}/finish")
    fun finish(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID): FinishSessionResponse {
        val session = sessionService.finish(principal.workspaceId, id)
        val summary = mapper.readValue(session.summary!!, SessionSummary::class.java)
        return FinishSessionResponse(session.id, summary.attemptCount, summary.accuracy, summary.durationMs)
    }

    private fun Session.toResponse(next: NextItemResponse?) =
        SessionResponse(id, scopeKind.name, scopeId, goalKind.name, goalValue, startedAt, endedAt, next)

    private fun SelectedItem?.toResponse(): NextItemResponse? = this?.let {
        NextItemResponse(it.itemId, it.type, it.stem, mapper.readTree(it.payloadJson), ItemMeta(it.conceptId, it.expectedSuccess))
    }

    private fun AttemptResult.toResponse(): SubmitAttemptResponse {
        val evidence = evidenceChunk?.let { EvidenceResponse(quote = it.text, sourceId = it.sourceId, chunkId = it.id, page = it.pageFrom) }
        val feedback = FeedbackResponse(
            correctResponse = mapper.readTree(grade.correctResponseJson),
            explanation = item.explanation,
            chosenOptionRationale = grade.chosenOptionRationale,
            evidence = evidence,
        )
        val learnerUpdate = LearnerUpdateResponse(item.conceptId, adaptResult.thetaBefore, adaptResult.thetaAfter, adaptResult.mastery, adaptResult.dueAt)
        return SubmitAttemptResponse(attempt.id, attempt.outcome.name, attempt.score, feedback, learnerUpdate, next.toResponse())
    }
}
