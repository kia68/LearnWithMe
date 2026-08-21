package de.optadata.odil.learnwithme.content.internal.service

import de.optadata.odil.learnwithme.content.internal.dedup.ContentHasher
import de.optadata.odil.learnwithme.content.internal.domain.Source
import de.optadata.odil.learnwithme.content.internal.domain.SourceKind
import de.optadata.odil.learnwithme.content.internal.domain.SourceStatus
import de.optadata.odil.learnwithme.content.internal.persistence.SourceRepository
import de.optadata.odil.learnwithme.platform.JobQueue
import de.optadata.odil.learnwithme.platform.JobType
import de.optadata.odil.learnwithme.platform.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** B1-B4, B7: nimmt einen Import entgegen, dedupliziert per SHA-256 (B7) und reiht bei
 * neuem Inhalt einen Ingest-Job ein (ADR-012) — die eigentliche Extraktion läuft asynchron
 * im [de.optadata.odil.learnwithme.content.internal.job.IngestJobHandler]. */
@Service
class SourceIngestionService(
    private val sourceRepository: SourceRepository,
    private val storageService: StorageService,
    private val jobQueue: JobQueue,
) {

    @Transactional
    fun ingestFile(workspaceId: UUID, filename: String, bytes: ByteArray, kind: SourceKind): Source {
        val hash = ContentHasher.sha256(bytes)
        sourceRepository.findByWorkspaceIdAndContentHash(workspaceId, hash)?.let { return it }

        val storageKey = storageService.store(workspaceId, filename, bytes)
        val source = sourceRepository.save(
            Source(
                workspaceId = workspaceId,
                kind = kind,
                title = filename,
                originUri = storageKey,
                contentHash = hash,
                status = SourceStatus.UPLOADED,
            ),
        )
        enqueueIngest(source)
        return source
    }

    @Transactional
    fun ingestUrl(workspaceId: UUID, url: String): Source {
        val normalizedUrl = url.trim()
        val hash = ContentHasher.sha256(normalizedUrl.toByteArray(Charsets.UTF_8))
        sourceRepository.findByWorkspaceIdAndContentHash(workspaceId, hash)?.let { return it }

        val source = sourceRepository.save(
            Source(
                workspaceId = workspaceId,
                kind = SourceKind.URL,
                title = normalizedUrl,
                originUri = normalizedUrl,
                contentHash = hash,
                status = SourceStatus.UPLOADED,
            ),
        )
        enqueueIngest(source)
        return source
    }

    @Transactional
    fun ingestHtmlSnippet(workspaceId: UUID, html: String, sourceUrl: String?, title: String?): Source {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val hash = ContentHasher.sha256(bytes)
        sourceRepository.findByWorkspaceIdAndContentHash(workspaceId, hash)?.let { return it }

        val storageKey = storageService.store(workspaceId, "snippet.html", bytes)
        val source = sourceRepository.save(
            Source(
                workspaceId = workspaceId,
                kind = SourceKind.HTML_SNIPPET,
                title = title?.takeIf { it.isNotBlank() } ?: sourceUrl ?: "Import aus Extension",
                originUri = storageKey,
                contentHash = hash,
                status = SourceStatus.UPLOADED,
            ),
        )
        enqueueIngest(source)
        return source
    }

    private fun enqueueIngest(source: Source) {
        jobQueue.enqueue(JobType.INGEST, "ingest:${source.id}", source.workspaceId, mapOf("sourceId" to source.id.toString()))
    }
}
