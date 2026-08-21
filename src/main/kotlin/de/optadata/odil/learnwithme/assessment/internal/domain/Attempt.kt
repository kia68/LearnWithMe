package de.optadata.odil.learnwithme.assessment.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Eine Beantwortung eines Items — append-only (N14): keine `var`-Felder außer der generierten ID. */
@Entity
@Table(name = "attempts")
class Attempt(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "session_id", nullable = false)
    val sessionId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "item_id", nullable = false)
    val itemId: UUID,

    /** Denormalisiert aus `items.type` — vermeidet einen Repository-Roundtrip pro Attempt für die
     * Typ-Rotation der Selection-Policy (§11.3, D9). */
    @Column(name = "item_type", nullable = false)
    val itemType: String,

    @Column(name = "concept_id", nullable = false)
    val conceptId: UUID,

    @Column(nullable = false)
    val response: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val outcome: AttemptOutcome,

    @Column(nullable = false)
    val score: Float,

    @Column(name = "elapsed_ms", nullable = false)
    val elapsedMs: Int,

    @Column(name = "theta_before", nullable = false)
    val thetaBefore: Float,

    @Column(name = "theta_after", nullable = false)
    val thetaAfter: Float,

    @Column(name = "p_expected", nullable = false)
    val pExpected: Float,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
