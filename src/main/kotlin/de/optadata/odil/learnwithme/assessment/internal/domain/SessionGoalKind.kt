package de.optadata.odil.learnwithme.assessment.internal.domain

/** D1: Zielvorgabe einer Lernsession. `goalValue` ist bei `ITEM_COUNT` die Anzahl Items,
 * bei `DURATION` die Zielzeit in Minuten. */
enum class SessionGoalKind {
    ITEM_COUNT,
    DURATION,
}
