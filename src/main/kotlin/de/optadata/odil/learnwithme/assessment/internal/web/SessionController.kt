package de.optadata.odil.learnwithme.assessment.internal.web

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import de.optadata.odil.learnwithme.assessment.internal.domain.Session
import de.optadata.odil.learnwithme.assessment.internal.domain.SessionGoalKind
import de.optadata.odil.learnwithme.assessment.internal.domain.SessionScopeKind
import de.optadata.odil.learnwithme.assessment.internal.selection.SelectedItem
import de.optadata.odil.learnwithme.assessment.internal.service.AttemptResult
import de.optadata.odil.learnwithme.assessment.internal.service.AttemptService
import de.optadata.odil.learnwithme.assessment.internal.service.GradeStatus
import de.optadata.odil.learnwithme.assessment.internal.service.SessionService
import de.optadata.odil.learnwithme.assessment.internal.service.SessionSummary
import de.optadata.odil.learnwithme.assessment.internal.service.SubmitOutcome
import de.optadata.odil.learnwithme.assessment.internal.web.dto.ErrorAnalysisResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.EvidenceResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.FeedbackResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.FinishSessionResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.GradeStatusResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.ItemMeta
import de.optadata.odil.learnwithme.assessment.internal.web.dto.LearnerUpdateResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.NextItemResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.PendingAttemptResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SessionResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SkipRequest
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SkipResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.StartSessionRequest
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SubmitAttemptRequest
import de.optadata.odil.learnwithme.assessment.internal.web.dto.SubmitAttemptResponse
import de.optadata.odil.learnwithme.shared.JsonMapper
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

    /** Epic H: `SHORT_ANSWER` liefert 202 statt 200 (kein LLM im kritischen Pfad, N1) —
     * `ResponseEntity<*>` löscht den Body-Typ für springdoc, deshalb hier explizit annotiert, damit
     * der generierte TS-Client (ADR-011) beide Formen kennt statt nur eine (oder keine). */
    @ApiResponses(
        ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = SubmitAttemptResponse::class))]),
        ApiResponse(responseCode = "202", content = [Content(schema = Schema(implementation = PendingAttemptResponse::class))]),
    )
    @PostMapping("/api/v1/sessions/{id}/attempts")
    fun submitAttempt(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @PathVariable id: UUID,
        @RequestBody request: SubmitAttemptRequest,
    ): ResponseEntity<*> =
        when (val outcome = attemptService.submit(principal.workspaceId, principal.userId, id, request.itemId, request.response, request.elapsedMs)) {
            is SubmitOutcome.Graded -> ResponseEntity.ok(outcome.result.toResponse())
            SubmitOutcome.Pending -> ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(PendingAttemptResponse(sessionService.peekNext(principal.workspaceId, id).toResponse()))
        }

    /** Epic H: Poll-Ziel nach einem 202 auf `POST attempts` (`SHORT_ANSWER`, async LLM-Rubric-Grading). */
    @GetMapping("/api/v1/sessions/{id}/items/{itemId}/grade")
    fun gradeStatus(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @PathVariable id: UUID,
        @PathVariable itemId: UUID,
    ): GradeStatusResponse = when (val status = attemptService.gradeStatus(principal.workspaceId, id, itemId)) {
        GradeStatus.Pending -> GradeStatusResponse("PENDING", null, null, null, null, null, null)
        is GradeStatus.Graded -> {
            val r = status.result.toResponse()
            GradeStatusResponse("GRADED", r.attemptId, r.outcome, r.score, r.feedback, r.errorAnalysis, r.learnerUpdate)
        }
        is GradeStatus.Failed -> GradeStatusResponse("FAILED", null, null, null, null, null, null, status.lastError)
    }

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

    /** Härtung (Epic H entdeckte das Muster erstmals bei `SHORT_ANSWER`s `referenceAnswer`, siehe
     * `docs/progress.md` „Bekannte Lücken" — hier auf alle sieben Typen ausgeweitet): jedes Feld,
     * das die Lösung trägt, wird aus dem an den Client gehenden `next`-Payload entfernt, bevor eine
     * Antwort abgegeben wurde — trivial im Network-Tab ablesbar sonst und die Frage sinnlos machend.
     * Betrifft NUR den Vorschau-Pfad (`next`/`peekNext`); `AttemptResult.toResponse()` nach dem
     * Absenden zeigt die Lösung weiterhin vollständig (D4-Feedback braucht sie). `rubric` bei
     * `SHORT_ANSWER` bleibt sichtbar (PLAN.md §4.2 E4 will das explizit). */
    private fun stripAnswerFields(type: String, payload: ObjectNode) {
        when (type) {
            "MC_SINGLE", "MC_MULTI" -> (payload.get("options") as? ArrayNode)?.forEach { option ->
                if (option is ObjectNode) {
                    option.remove("correct")
                    option.remove("rationale")
                    option.remove("misconceptionCategory")
                }
            }
            "TRUE_FALSE" -> {
                payload.remove("answer")
                payload.remove("rationale")
            }
            "ORDERING" -> payload.remove("correctOrder")
            "MATCHING" -> payload.remove("pairs")
            "CLOZE" -> (payload.get("blanks") as? ArrayNode)?.forEach { blank ->
                if (blank is ObjectNode) blank.putArray("accepted")
            }
            "SHORT_ANSWER" -> payload.remove("referenceAnswer")
        }
    }

    private fun SelectedItem?.toResponse(): NextItemResponse? = this?.let {
        val payload = mapper.readTree(it.payloadJson)
        if (payload is ObjectNode) stripAnswerFields(it.type, payload)
        NextItemResponse(it.itemId, it.type, it.stem, mapper.convertValue(payload, Any::class.java), ItemMeta(it.conceptId, it.expectedSuccess))
    }

    private fun AttemptResult.toResponse(): SubmitAttemptResponse {
        val evidence = evidenceChunk?.let { EvidenceResponse(quote = it.text, sourceId = it.sourceId, chunkId = it.id, page = it.pageFrom) }
        val feedback = FeedbackResponse(
            correctResponse = mapper.readValue(grade.correctResponseJson, Any::class.java),
            explanation = item.explanation,
            chosenOptionRationale = grade.chosenOptionRationale,
            evidence = evidence,
        )
        val learnerUpdate = LearnerUpdateResponse(item.conceptId, adaptResult.thetaBefore, adaptResult.thetaAfter, adaptResult.mastery, adaptResult.dueAt)
        val errorAnalysisResponse = errorAnalysis?.let { ErrorAnalysisResponse(it.category, it.confidence, it.note) }
        return SubmitAttemptResponse(attempt.id, attempt.outcome.name, attempt.score, feedback, errorAnalysisResponse, learnerUpdate, next.toResponse())
    }
}
