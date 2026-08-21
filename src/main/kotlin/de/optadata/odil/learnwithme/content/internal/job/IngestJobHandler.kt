package de.optadata.odil.learnwithme.content.internal.job

import com.fasterxml.jackson.databind.ObjectMapper
import de.optadata.odil.learnwithme.content.SourceIndexed
import de.optadata.odil.learnwithme.content.internal.chunking.ChunkSpan
import de.optadata.odil.learnwithme.content.internal.chunking.Chunker
import de.optadata.odil.learnwithme.content.internal.config.IngestionProperties
import de.optadata.odil.learnwithme.content.internal.domain.Chunk
import de.optadata.odil.learnwithme.content.internal.domain.Section
import de.optadata.odil.learnwithme.content.internal.domain.SourceKind
import de.optadata.odil.learnwithme.content.internal.domain.SourceStatus
import de.optadata.odil.learnwithme.content.internal.extraction.BuiltSection
import de.optadata.odil.learnwithme.content.internal.extraction.MarkdownSectionDetector
import de.optadata.odil.learnwithme.content.internal.extraction.SectionPersister
import de.optadata.odil.learnwithme.content.internal.extraction.TikaExtractionService
import de.optadata.odil.learnwithme.content.internal.extraction.WebExtractionService
import de.optadata.odil.learnwithme.content.internal.ocr.OcrNeedDetector
import de.optadata.odil.learnwithme.content.internal.persistence.ChunkRepository
import de.optadata.odil.learnwithme.content.internal.persistence.SourceRepository
import de.optadata.odil.learnwithme.content.internal.sse.SourceEventBroadcaster
import de.optadata.odil.learnwithme.platform.JobHandler
import de.optadata.odil.learnwithme.platform.JobType
import de.optadata.odil.learnwithme.platform.StorageService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Kern-Flow 1 (§6.4): Extract → (Struktur+)Chunk → Index, mit SSE-Statuswechseln bei jedem
 * Schritt (B1). Läuft als [JobHandler], vom `platform`-Worker aufgerufen (ADR-012).
 *
 * Jede einzelne `repository.save(...)`/`saveAll(...)`-Aufruf ist für sich transaktional
 * (Spring Data Default) — bewusst kein `@Transactional` auf dieser Klasse: Methoden, die sich
 * selbst aufrufen, würden die Spring-AOP-Proxy-basierte Transaktionsgrenze sonst stillschweigend
 * umgehen (Self-Invocation).
 */
@Component
class IngestJobHandler(
    private val sourceRepository: SourceRepository,
    private val chunkRepository: ChunkRepository,
    private val sectionPersister: SectionPersister,
    private val storageService: StorageService,
    private val tikaExtractionService: TikaExtractionService,
    private val webExtractionService: WebExtractionService,
    private val ingestionProperties: IngestionProperties,
    private val broadcaster: SourceEventBroadcaster,
    private val eventPublisher: ApplicationEventPublisher,
) : JobHandler {

    override val type = JobType.INGEST
    private val objectMapper = ObjectMapper()

    override fun handle(jobId: UUID, payloadJson: String) {
        val sourceId = UUID.fromString(objectMapper.readTree(payloadJson).get("sourceId").asText())
        try {
            process(sourceId)
        } catch (ex: Exception) {
            fail(sourceId, ex.message ?: ex.javaClass.simpleName)
            throw ex // Job-Attempts/Retry bleiben Sache des JobWorker
        }
    }

    private fun process(sourceId: UUID) {
        val source = sourceRepository.findById(sourceId)
            .orElseThrow { IllegalStateException("Source $sourceId nicht gefunden") }

        setStatus(sourceId, SourceStatus.EXTRACTING)
        val extraction = extract(source.kind, source.originUri)

        if (extraction.plainText.isBlank()) {
            fail(sourceId, "Keine extrahierbaren Inhalte gefunden.")
            return
        }
        if (extraction.partial) {
            markPartial(sourceId, extraction)
            return
        }
        if ((extraction.pageCount ?: 0) > ingestionProperties.maxPages) {
            fail(sourceId, "Dokument hat ${extraction.pageCount} Seiten, Limit sind ${ingestionProperties.maxPages} (B1).")
            return
        }

        val needsOcr = source.kind == SourceKind.PDF &&
            OcrNeedDetector.needsOcr(extraction.plainText.length, extraction.pageCount, ingestionProperties.ocrTextDensityThreshold)

        setStatus(sourceId, SourceStatus.CHUNKING)
        val savedSections = sectionPersister.persist(sourceId, extraction.sections)
        val chunkSpans = Chunker(ingestionProperties.chunkTargetTokens, ingestionProperties.chunkOverlapTokens)
            .chunk(extraction.plainText)

        setStatus(sourceId, SourceStatus.INDEXING)
        saveChunks(sourceId, chunkSpans, extraction.sections, savedSections)

        finish(sourceId, extraction, needsOcr)
        broadcaster.push(sourceId, SourceStatus.READY.name)
        eventPublisher.publishEvent(SourceIndexed(sourceId, source.workspaceId))
    }

    private fun extract(kind: SourceKind, originUri: String?): ExtractionOutcome = when (kind) {
        SourceKind.URL -> {
            val web = webExtractionService.fetchAndExtract(requireNotNull(originUri) { "URL-Source ohne origin_uri" })
            ExtractionOutcome(web.plainText, web.sections, null, web.title, web.partial, web.partialReason)
        }
        SourceKind.HTML_SNIPPET -> {
            val bytes = storageService.load(requireNotNull(originUri) { "HTML-Snippet ohne Storage-Key" })
            val web = webExtractionService.extractFromHtml(String(bytes, Charsets.UTF_8), null)
            ExtractionOutcome(web.plainText, web.sections, null, web.title, web.partial, web.partialReason)
        }
        else -> {
            val bytes = storageService.load(requireNotNull(originUri) { "${kind.name}-Source ohne Storage-Key" })
            val tika = tikaExtractionService.extract(bytes)
            val sections = if (kind == SourceKind.TEXT) MarkdownSectionDetector.detect(tika.plainText) else tika.sections
            ExtractionOutcome(tika.plainText, sections, tika.pageCount, null, false, null)
        }
    }

    private fun saveChunks(sourceId: UUID, spans: List<ChunkSpan>, built: List<BuiltSection>, saved: List<Section>) {
        val chunks = spans.map { span ->
            var sectionId: UUID? = null
            for (i in built.indices) {
                if (built[i].charFrom <= span.charFrom) sectionId = saved[i].id else break
            }
            Chunk(
                sourceId = sourceId,
                sectionId = sectionId,
                ordinal = span.ordinal,
                text = span.text,
                tokenCount = span.tokenCount,
                charFrom = span.charFrom,
                charTo = span.charTo,
            )
        }
        chunkRepository.saveAll(chunks)
    }

    private fun setStatus(sourceId: UUID, status: SourceStatus) {
        val source = sourceRepository.findById(sourceId).orElseThrow()
        source.status = status
        sourceRepository.save(source)
        broadcaster.push(sourceId, status.name)
    }

    private fun markPartial(sourceId: UUID, extraction: ExtractionOutcome) {
        val source = sourceRepository.findById(sourceId).orElseThrow()
        source.status = SourceStatus.PARTIAL
        source.failureReason = extraction.partialReason
        extraction.title?.let { if (it.isNotBlank()) source.title = it }
        sourceRepository.save(source)
        broadcaster.push(sourceId, SourceStatus.PARTIAL.name)
    }

    private fun finish(sourceId: UUID, extraction: ExtractionOutcome, needsOcr: Boolean) {
        val source = sourceRepository.findById(sourceId).orElseThrow()
        source.status = SourceStatus.READY
        source.pageCount = extraction.pageCount
        source.needsOcr = needsOcr
        extraction.title?.let { if (it.isNotBlank()) source.title = it }
        sourceRepository.save(source)
    }

    private fun fail(sourceId: UUID, reason: String) {
        val source = sourceRepository.findById(sourceId).orElse(null) ?: return
        source.status = SourceStatus.FAILED
        source.failureReason = reason
        sourceRepository.save(source)
        broadcaster.push(sourceId, SourceStatus.FAILED.name)
    }

    private data class ExtractionOutcome(
        val plainText: String,
        val sections: List<BuiltSection>,
        val pageCount: Int?,
        val title: String?,
        val partial: Boolean,
        val partialReason: String?,
    )
}
