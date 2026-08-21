package de.optadata.odil.learnwithme.assessment.internal.domain

/** D1: Umfang einer Lernsession (§8.1). `scopeId` ist bei `SOURCE`/`CONCEPT` die jeweilige ID,
 * bei `DUE_REVIEW`/`MIXED` leer. */
enum class SessionScopeKind {
    SOURCE,
    CONCEPT,
    DUE_REVIEW,
    MIXED,
}
