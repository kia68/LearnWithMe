package de.optadata.odil.learnwithme.authoring.internal.web

import de.optadata.odil.learnwithme.authoring.internal.export.AnkiExporter
import de.optadata.odil.learnwithme.authoring.internal.export.QtiExporter
import de.optadata.odil.learnwithme.authoring.internal.service.ItemExportService
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.util.UUID

/** M6-Nachtrag (PLAN.md §14): Export der veröffentlichten Fragen einer Source nach Anki
 * (Tab-getrennter Text, Ankis nativer Text-Importer) und QTI 2.1 (ein `assessmentItem`-XML pro
 * Frage, als ZIP) — siehe [AnkiExporter]/[QtiExporter] für die jeweiligen Format-Entscheidungen. */
@RestController
class ItemExportController(private val exportService: ItemExportService) {

    @GetMapping("/api/v1/sources/{sourceId}/export/anki")
    fun exportAnki(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable sourceId: UUID): ResponseEntity<ByteArray> {
        val items = exportService.listPublishedForSource(principal.workspaceId, sourceId)
        val body = AnkiExporter.toTsv(items).toByteArray(StandardCharsets.UTF_8)
        return download(body, "anki-export-$sourceId.txt", MediaType.parseMediaType("text/plain;charset=UTF-8"))
    }

    @GetMapping("/api/v1/sources/{sourceId}/export/qti")
    fun exportQti(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable sourceId: UUID): ResponseEntity<ByteArray> {
        val items = exportService.listPublishedForSource(principal.workspaceId, sourceId)
        val body = QtiExporter.toZip(items)
        return download(body, "qti-export-$sourceId.zip", MediaType.parseMediaType("application/zip"))
    }

    private fun download(body: ByteArray, filename: String, contentType: MediaType): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
            .body(body)
}
