package de.optadata.odil.learnwithme.analytics.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Ein wiederkehrendes, benanntes Fehlmuster (E3): `occurrences >= 3` derselben [category] im
 * selben Konzept ist die "Misconception-Flag" selbst — kein separates Boolean-Feld nötig. */
@Entity
@Table(name = "misconceptions")
class Misconception(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "concept_id", nullable = false)
    val conceptId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val category: ErrorCategory,

    @Column(nullable = false)
    var occurrences: Int = 1,

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    val firstSeenAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
)
