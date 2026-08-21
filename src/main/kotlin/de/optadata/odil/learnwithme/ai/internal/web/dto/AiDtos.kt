package de.optadata.odil.learnwithme.ai.internal.web.dto

import de.optadata.odil.learnwithme.ai.internal.domain.AiProvider
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateCredentialRequest(
    val provider: AiProvider,
    @field:NotBlank val apiKey: String,
    val baseUrl: String? = null,
    val label: String? = null,
)

/** Niemals den Klartext-Key enthalten (ADR-009) — nur der Hint der letzten 4 Zeichen. */
data class CredentialResponse(
    val id: UUID,
    val provider: AiProvider,
    val label: String?,
    val keyHint: String,
    val baseUrl: String?,
    val region: String?,
    val status: String,
    val lastVerifiedAt: Instant?,
    val createdAt: Instant,
)

data class VerifyCredentialResponse(val status: String, val message: String, val lastVerifiedAt: Instant?)

data class ProviderInfo(val id: AiProvider, val displayName: String, val requiresBaseUrl: Boolean)

data class UsageBreakdownEntry(val task: String, val costMicros: Long, val inputTokens: Long, val outputTokens: Long)

data class QuotaInfo(val plan: String, val limitMicros: Long?, val usedMicros: Long, val exceeded: Boolean)

data class UsageSummaryResponse(
    val from: Instant,
    val to: Instant,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalCostMicros: Long,
    val topCostDrivers: List<UsageBreakdownEntry>,
    val quota: QuotaInfo,
)
