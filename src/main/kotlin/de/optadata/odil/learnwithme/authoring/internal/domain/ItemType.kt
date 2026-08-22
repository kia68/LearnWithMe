package de.optadata.odil.learnwithme.authoring.internal.domain

/** MVP-Fragetypen (§10.1) plus SHORT_ANSWER (Epic H, löst E4) sowie NUMERIC/CATEGORIZATION/
 * CODE_OUTPUT (M6-Nachtrag). HOTSPOT bleibt bewusst nicht gebaut: bräuchte eine eigene
 * Bild-Asset-Pipeline (kein Ingestion-Pfad für Frage-Bilder in dieser Codebase) plus
 * Koordinaten-Hit-Testing im Client — deutlich größerer Zuschnitt als die übrigen drei. */
enum class ItemType {
    MC_SINGLE,
    MC_MULTI,
    TRUE_FALSE,
    ORDERING,
    MATCHING,
    CLOZE,
    SHORT_ANSWER,
    NUMERIC,
    CATEGORIZATION,
    CODE_OUTPUT,
}
