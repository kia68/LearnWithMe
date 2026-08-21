package de.optadata.odil.learnwithme.assessment.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Eine zusammenhängende Folge von Attempts (D1). `summary` ist Jackson-JSON-TEXT (D1-Abschluss:
 * Anzahl, Genauigkeit, Dauer) — wie `items.payload` (Epic C) bewusst nicht JSONB. */
@Entity
@Table(name = "sessions")
class Session(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_kind", nullable = false)
    val scopeKind: SessionScopeKind,

    @Column(name = "scope_id")
    val scopeId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_kind", nullable = false)
    val goalKind: SessionGoalKind,

    @Column(name = "goal_value", nullable = false)
    val goalValue: Int,

    @Column(name = "started_at", nullable = false, updatable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column
    var summary: String? = null,
)
