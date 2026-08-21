package de.optadata.odil.learnwithme.analytics.internal.domain

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

/** Ein klassifizierter Fehler (E1) — append-only, wie `assessment.Attempt`. Kein JPA-`@ManyToOne`
 * auf den Attempt: `assessment` ist ein anderes Modul, `attemptId` bleibt eine lose ID-Referenz. */
@Entity
@Table(name = "error_events")
class ErrorEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "attempt_id", nullable = false)
    val attemptId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "concept_id", nullable = false)
    val conceptId: UUID,

    @Column(name = "item_id", nullable = false)
    val itemId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val category: ErrorCategory,

    @Column
    val detail: String? = null,

    @Column(nullable = false)
    val confidence: Float,

    @Enumerated(EnumType.STRING)
    @Column(name = "detected_by", nullable = false)
    val detectedBy: DetectionMethod,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
