package de.optadata.odil.learnwithme.ai.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Ein LLM-Aufruf (N13, §16). Wird ab dem `LlmGateway` (M1) befüllt — in Epic A
 * existiert nur die Persistenz- und Aggregations-Infrastruktur für A4. */
@Entity
@Table(name = "llm_usage")
class LlmUsageRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(nullable = false)
    val task: String,

    @Column(nullable = false)
    val provider: String,

    @Column(nullable = false)
    val model: String,

    @Column(name = "input_tokens", nullable = false)
    val inputTokens: Int,

    @Column(name = "output_tokens", nullable = false)
    val outputTokens: Int,

    @Column(name = "cached_tokens", nullable = false)
    val cachedTokens: Int = 0,

    @Column(name = "cost_micros", nullable = false)
    val costMicros: Long,

    @Column(name = "latency_ms", nullable = false)
    val latencyMs: Int,

    @Column(nullable = false)
    val outcome: String,

    @Column(name = "correlation_id")
    val correlationId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
