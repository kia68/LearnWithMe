package de.optadata.odil.learnwithme.adaptivity.internal.domain

/** FSRS-Kartenzustand (§11.4). */
enum class LearnerState {
    NEW,
    LEARNING,
    REVIEW,
    RELEARNING,
}
