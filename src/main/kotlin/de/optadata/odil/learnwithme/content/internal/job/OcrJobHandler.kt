package de.optadata.odil.learnwithme.content.internal.job

import com.fasterxml.jackson.databind.ObjectMapper
import de.optadata.odil.learnwithme.content.internal.chunking.Chunker
import de.optadata.odil.learnwithme.content.internal.config.IngestionProperties
import de.optadata.odil.learnwithme.content.internal.domain.Chunk
import de.optadata.odil.learnwithme.content.internal.domain.SourceStatus
import de.optadata.odil.learnwithme.content.internal.persistence.ChunkRepository
import de.optadata.odil.learnwithme.content.internal.persistence.SectionRepository
import de.optadata.odil.learnwithme.content.internal.persistence.SourceRepository
import de.optadata.odil.learnwithme.content.internal.sse.SourceEventBroadcaster
import de.optadata.odil.learnwithme.platform.JobHandler
import de.optadata.odil.learnwithme.platform.JobType
import de.optadata.odil.learnwithme.platform.StorageService
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.parser.ocr.TesseractOCRConfig
import org.apache.tika.parser.pdf.PDFParserConfig
import org.apache.tika.sax.BodyContentHandler
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.util.UUID

/**
 * B5: OCR-Nachlauf für als `needsOcr` markierte PDFs. Best-effort — Tika delegiert OCR an eine
 * lokale Tesseract-Installation; ist keine vorhanden, liefert Tika denselben (dünnen) Text wie
 * beim ursprünglichen Import zurück, und dieser Job schlägt mit einer klaren Fehlermeldung fehl
 * statt stillschweigend nichts zu tun. In dieser Entwicklungsumgebung ohne Tesseract nicht
 * verifizierbar — gleiche Kategorie Lücke wie die Docker-abhängigen Tests aus Epic A.
 */
@Component
class OcrJobHandler(
    private val sourceRepository: SourceRepository,
    private val chunkRepository: ChunkRepository,
    private val sectionRepository: SectionRepository,
    private val storageService: StorageService,
    private val ingestionProperties: IngestionProperties,
    private val broadcaster: SourceEventBroadcaster,
) : JobHandler {

    override val type = JobType.OCR
    private val objectMapper = ObjectMapper()

    override fun handle(jobId: UUID, payloadJson: String) {
        val sourceId = UUID.fromString(objectMapper.readTree(payloadJson).get("sourceId").asText())
        val source = sourceRepository.findById(sourceId)
            .orElseThrow { IllegalStateException("Source $sourceId nicht gefunden") }
        val previousTextLength = chunkRepository.findAllBySourceIdOrderByOrdinal(sourceId).sumOf { it.text.length }

        val bytes = storageService.load(requireNotNull(source.originUri) { "Source ohne Storage-Key" })
        val ocrText = runOcr(bytes)

        if (ocrText.length <= previousTextLength) {
            throw IllegalStateException(
                "OCR ergab keine zusätzliche Textmenge — Tesseract ist auf diesem Host vermutlich nicht installiert.",
            )
        }

        // Struktur aus OCR-Text ist unzuverlässig (keine Formatierung mehr erkennbar) — Sections
        // verwerfen, Chunks aus dem OCR-Text neu aufbauen.
        chunkRepository.deleteAllBySourceId(sourceId)
        sectionRepository.deleteAllBySourceId(sourceId)
        val spans = Chunker(ingestionProperties.chunkTargetTokens, ingestionProperties.chunkOverlapTokens).chunk(ocrText)
        chunkRepository.saveAll(
            spans.map {
                Chunk(
                    sourceId = sourceId,
                    ordinal = it.ordinal,
                    text = it.text,
                    tokenCount = it.tokenCount,
                    charFrom = it.charFrom,
                    charTo = it.charTo,
                )
            },
        )

        source.needsOcr = false
        source.status = SourceStatus.READY
        sourceRepository.save(source)
        broadcaster.push(sourceId, SourceStatus.READY.name)
    }

    private fun runOcr(bytes: ByteArray): String {
        val parser = AutoDetectParser()
        val metadata = Metadata()
        val handler = BodyContentHandler(-1)
        val context = ParseContext()
        context.set(TesseractOCRConfig::class.java, TesseractOCRConfig())
        context.set(
            PDFParserConfig::class.java,
            PDFParserConfig().apply { setOcrStrategy(PDFParserConfig.OCR_STRATEGY.OCR_AND_TEXT_EXTRACTION) },
        )
        ByteArrayInputStream(bytes).use { parser.parse(it, handler, metadata, context) }
        return handler.toString()
    }
}
