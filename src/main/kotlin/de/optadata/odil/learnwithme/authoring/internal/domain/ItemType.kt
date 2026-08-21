package de.optadata.odil.learnwithme.authoring.internal.domain

/** MVP-Fragetypen (§10.1) plus SHORT_ANSWER (Epic H, löst E4). NUMERIC/CATEGORIZATION (Prio S)
 * und HOTSPOT/CODE_OUTPUT (Prio C) bleiben bewusst nicht gebaut: kein Story-Bedarf. */
enum class ItemType {
    MC_SINGLE,
    MC_MULTI,
    TRUE_FALSE,
    ORDERING,
    MATCHING,
    CLOZE,
    SHORT_ANSWER,
}
