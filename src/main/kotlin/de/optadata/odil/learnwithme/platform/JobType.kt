package de.optadata.odil.learnwithme.platform

/** Job-Arten der DB-gestützten Queue (ADR-012). */
enum class JobType {
    INGEST,
    OCR,
    GENERATE_ITEMS,
    GENERATE_PARAPHRASE,
}
