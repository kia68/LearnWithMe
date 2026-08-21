package de.optadata.odil.learnwithme.content.internal.domain

/** Ingestion-Zustandsmaschine (B1): `UPLOADED → EXTRACTING → CHUNKING → INDEXING → READY|FAILED`,
 * `PARTIAL` zusätzlich für erkannte Paywall/JS-only-Importe (B2). */
enum class SourceStatus {
    UPLOADED,
    EXTRACTING,
    CHUNKING,
    INDEXING,
    READY,
    PARTIAL,
    FAILED,
}
