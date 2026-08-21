package de.optadata.odil.learnwithme.assessment.internal.web.dto

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class StartSessionRequest(val scopeKind: String, val scopeId: UUID?, val goalKind: String, val goalValue: Int)

data class ItemMeta(val conceptId: UUID, val expectedSuccess: Float)

data class NextItemResponse(val itemId: UUID, val type: String, val stem: String, val payload: JsonNode, val meta: ItemMeta)

data class SessionResponse(
    val sessionId: UUID,
    val scopeKind: String,
    val scopeId: UUID?,
    val goalKind: String,
    val goalValue: Int,
    val startedAt: Instant,
    val endedAt: Instant?,
    val next: NextItemResponse?,
)

data class SubmitAttemptRequest(val itemId: UUID, val response: JsonNode, val elapsedMs: Int)

data class EvidenceResponse(val quote: String, val sourceId: UUID, val chunkId: UUID, val page: Int?)

data class FeedbackResponse(
    val correctResponse: JsonNode,
    val explanation: String,
    val chosenOptionRationale: String?,
    val evidence: EvidenceResponse?,
)

data class LearnerUpdateResponse(val conceptId: UUID, val thetaBefore: Float, val thetaAfter: Float, val mastery: Float, val nextDueAt: Instant)

data class SubmitAttemptResponse(
    val attemptId: Long,
    val outcome: String,
    val score: Float,
    val feedback: FeedbackResponse,
    val learnerUpdate: LearnerUpdateResponse,
    val next: NextItemResponse?,
)

data class SkipRequest(val itemId: UUID, val reason: String? = null)

data class SkipResponse(val next: NextItemResponse?)

data class FinishSessionResponse(val sessionId: UUID, val attemptCount: Int, val accuracy: Float, val durationMs: Long)
