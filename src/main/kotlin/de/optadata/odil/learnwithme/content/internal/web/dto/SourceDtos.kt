package de.optadata.odil.learnwithme.content.internal.web.dto

import java.time.Instant
import java.util.UUID

data class SourceResponse(
    val id: UUID,
    val kind: String,
    val title: String,
    val status: String,
    val failureReason: String?,
    val pageCount: Int?,
    val needsOcr: Boolean,
    val createdAt: Instant,
)

data class SourcePageResponse(val items: List<SourceResponse>, val page: Int, val size: Int, val totalElements: Long)

data class SectionResponse(
    val id: UUID,
    val parentId: UUID?,
    val ordinal: Int,
    val level: Int,
    val title: String,
    val excluded: Boolean,
)

/** `url` allein → B2 (URL-Import). `html` gesetzt → B3 (Extension-DOM-Snippet), `url`/`title`
 * dann nur als Metadaten. */
data class ImportSourceRequest(val url: String? = null, val html: String? = null, val title: String? = null)

data class ExcludeSectionRequest(val excluded: Boolean)

data class OcrTriggerResponse(val jobId: UUID)
