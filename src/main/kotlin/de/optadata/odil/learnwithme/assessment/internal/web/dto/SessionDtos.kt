package de.optadata.odil.learnwithme.assessment.internal.web.dto

import java.time.Instant
import java.util.UUID

data class StartSessionRequest(val scopeKind: String, val scopeId: UUID?, val goalKind: String, val goalValue: Int)

data class ItemMeta(val conceptId: UUID, val expectedSuccess: Float)

/** [payload]/[response]/[correctResponse] in diesem File sind bewusst `Any`, nicht
 * `com.fasterxml.jackson.databind.JsonNode`: Spring Boot 4s `spring-boot-starter-jackson`
 * bringt standardmäßig Jackson **3.x** mit (`tools.jackson.*`), das den klassischen
 * Jackson-2-Typ `JsonNode` (aus `shared.JsonMapper`) nicht als eigenes Baum-Modell erkennt und
 * beim Serialisieren auf Bean-Introspektion zurückfällt (`{"array":false,"object":true,...}`
 * statt des eigentlichen JSON) — nur beim allerersten echten HTTP-Testlauf entdeckt (Epic F),
 * da zuvor kein Client diese Endpunkte je aufgerufen hatte. `Any` (Map/List/Primitiv-Baum) ist
 * für JEDE Jackson-Version verlustfrei; die Umwandlung zurück zu `JsonNode` für das interne
 * Grading (`ResponseGrader`) passiert an der Controller-/Service-Grenze (`AttemptService`). */
data class NextItemResponse(val itemId: UUID, val type: String, val stem: String, val payload: Any, val meta: ItemMeta)

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

data class SubmitAttemptRequest(val itemId: UUID, val response: Any, val elapsedMs: Int)

data class EvidenceResponse(val quote: String, val sourceId: UUID, val chunkId: UUID, val page: Int?)

data class FeedbackResponse(
    val correctResponse: Any,
    val explanation: String,
    val chosenOptionRationale: String?,
    val evidence: EvidenceResponse?,
)

data class LearnerUpdateResponse(val conceptId: UUID, val thetaBefore: Float, val thetaAfter: Float, val mastery: Float, val nextDueAt: Instant)

data class ErrorAnalysisResponse(val category: String, val confidence: Float, val note: String?)

data class SubmitAttemptResponse(
    val attemptId: Long,
    val outcome: String,
    val score: Float,
    val feedback: FeedbackResponse,
    val errorAnalysis: ErrorAnalysisResponse?,
    val learnerUpdate: LearnerUpdateResponse,
    val next: NextItemResponse?,
)

data class SkipRequest(val itemId: UUID, val reason: String? = null)

data class SkipResponse(val next: NextItemResponse?)

data class FinishSessionResponse(val sessionId: UUID, val attemptCount: Int, val accuracy: Float, val durationMs: Long)
