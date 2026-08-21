package de.optadata.odil.learnwithme.analytics.internal.domain

/** Fehlertaxonomie (E1, §11.5). */
enum class ErrorCategory {
    FACTUAL_GAP,
    TERM_CONFUSION,
    CONCEPT_CONFUSION,
    PROCEDURAL,
    CARELESS,
    AMBIGUOUS_ITEM,
}

/** Wie die Kategorie ermittelt wurde — beide ohne Laufzeit-LLM (§11.5). */
enum class DetectionMethod {
    /** Aus der `misconceptionCategory` einer gewählten Distraktor-Option (Generierungszeit, C8/E1). */
    RATIONALE,

    /** Aus Attempt-Signalen (θ vs. Item-Schwierigkeit, Antwortzeit) ohne Distraktor-Tag. */
    HEURISTIC,
}
