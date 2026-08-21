package de.optadata.odil.learnwithme.content.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Ein importiertes Dokument (B1-B7). [originUri] ist der S3-Key der Rohdatei
 * (PDF/DOCX/EPUB) oder die Quell-URL (URL-Import) — bei HTML_SNIPPET/TEXT ungenutzt. */
@Entity
@Table(name = "sources")
class Source(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val kind: SourceKind,

    @Column(nullable = false)
    var title: String,

    @Column(name = "origin_uri")
    var originUri: String? = null,

    @Column(name = "content_hash", nullable = false)
    var contentHash: ByteArray,

    var language: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SourceStatus,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "page_count")
    var pageCount: Int? = null,

    @Column(name = "needs_ocr", nullable = false)
    var needsOcr: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
