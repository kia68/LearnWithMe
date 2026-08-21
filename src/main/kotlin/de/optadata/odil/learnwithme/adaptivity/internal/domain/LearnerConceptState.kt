package de.optadata.odil.learnwithme.adaptivity.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class LearnerConceptStateId(
    @Column(name = "user_id")
    val userId: UUID = UUID.randomUUID(),
    @Column(name = "concept_id")
    val conceptId: UUID = UUID.randomUUID(),
) : Serializable

/** Fähigkeits- (Elo) und Gedächtnisstand (FSRS) eines Nutzers zu einem Konzept — zwei getrennte
 * Modelle in einer Zeile (§11.1). `workspace_id` ist eine bewusste Ergänzung gegenüber PLAN.md
 * §8.1 (N9, siehe V6-Migration). */
@Entity
@Table(name = "learner_concept_state")
class LearnerConceptState(
    @EmbeddedId
    val id: LearnerConceptStateId,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(nullable = false)
    var theta: Float = 0.0f,

    @Column(name = "theta_n", nullable = false)
    var thetaN: Int = 0,

    @Column(nullable = false)
    var mastery: Float = 0.5f,

    @Column(name = "fsrs_stability")
    var fsrsStability: Float? = null,

    @Column(name = "fsrs_difficulty")
    var fsrsDifficulty: Float? = null,

    @Column(name = "last_review_at")
    var lastReviewAt: Instant? = null,

    @Column(name = "due_at")
    var dueAt: Instant? = null,

    @Column(nullable = false)
    var lapses: Int = 0,

    @Column(nullable = false)
    var reps: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: LearnerState = LearnerState.NEW,
) {
    val userId: UUID get() = id.userId
    val conceptId: UUID get() = id.conceptId
}
