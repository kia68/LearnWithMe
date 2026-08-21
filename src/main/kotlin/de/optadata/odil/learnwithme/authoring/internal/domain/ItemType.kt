package de.optadata.odil.learnwithme.authoring.internal.domain

/** MVP-Fragetypen (§10.1) — SHORT_ANSWER/NUMERIC/CATEGORIZATION (Prio S) und
 * HOTSPOT/CODE_OUTPUT (Prio C) sind bewusst nicht gebaut: kein Epic-C-Story-Bedarf. */
enum class ItemType {
    MC_SINGLE,
    MC_MULTI,
    TRUE_FALSE,
    ORDERING,
    MATCHING,
    CLOZE,
}
