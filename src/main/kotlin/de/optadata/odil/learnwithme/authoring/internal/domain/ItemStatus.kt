package de.optadata.odil.learnwithme.authoring.internal.domain

/** C7: `DRAFT → PUBLISHED | REJECTED`, `PUBLISHED → RETIRED`. Der genaue Ablehnungsgrund
 * (ungrounded/strukturell/duplikat) steckt in `Item.quality`, nicht im Status selbst. */
enum class ItemStatus {
    DRAFT,
    PUBLISHED,
    REJECTED,
    RETIRED,
}
