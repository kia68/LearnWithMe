package de.optadata.odil.learnwithme.content.internal.web

import de.optadata.odil.learnwithme.content.internal.domain.Section
import de.optadata.odil.learnwithme.content.internal.domain.Source
import de.optadata.odil.learnwithme.content.internal.service.OcrTriggerService
import de.optadata.odil.learnwithme.content.internal.service.SourceIngestionService
import de.optadata.odil.learnwithme.content.internal.service.SourceKindDetector
import de.optadata.odil.learnwithme.content.internal.service.SourceQueryService
import de.optadata.odil.learnwithme.content.internal.sse.SourceEventBroadcaster
import de.optadata.odil.learnwithme.content.internal.web.dto.ExcludeSectionRequest
import de.optadata.odil.learnwithme.content.internal.web.dto.ImportSourceRequest
import de.optadata.odil.learnwithme.content.internal.web.dto.OcrTriggerResponse
import de.optadata.odil.learnwithme.content.internal.web.dto.SectionResponse
import de.optadata.odil.learnwithme.content.internal.web.dto.SourcePageResponse
import de.optadata.odil.learnwithme.content.internal.web.dto.SourceResponse
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import de.optadata.odil.learnwithme.shared.ApiException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

/** B1-B7: Dokumenten-Import & Ingestion. */
@RestController
@RequestMapping("/api/v1/sources")
class SourceController(
    private val ingestionService: SourceIngestionService,
    private val queryService: SourceQueryService,
    private val ocrTriggerService: OcrTriggerService,
    private val broadcaster: SourceEventBroadcaster,
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun upload(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @RequestPart("file") file: MultipartFile,
    ): SourceResponse {
        val filename = file.originalFilename ?: "upload"
        val kind = SourceKindDetector.detect(filename, file.contentType)
        return ingestionService.ingestFile(principal.workspaceId, filename, file.bytes, kind).toResponse()
    }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun import(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @RequestBody request: ImportSourceRequest,
    ): SourceResponse {
        val source = when {
            !request.html.isNullOrBlank() ->
                ingestionService.ingestHtmlSnippet(principal.workspaceId, request.html, request.url, request.title)
            !request.url.isNullOrBlank() -> ingestionService.ingestUrl(principal.workspaceId, request.url)
            else -> throw ApiException(
                HttpStatus.BAD_REQUEST,
                "Ungültige Anfrage",
                "Entweder `url` oder `html` muss gesetzt sein.",
            )
        }
        return source.toResponse()
    }

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): SourcePageResponse {
        val result = queryService.list(principal.workspaceId, page, size)
        return SourcePageResponse(result.content.map { it.toResponse() }, page, size, result.totalElements)
    }

    @GetMapping("/{id}")
    fun get(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID): SourceResponse =
        queryService.get(principal.workspaceId, id).toResponse()

    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID): SseEmitter {
        queryService.get(principal.workspaceId, id) // Ownership-Check
        return broadcaster.subscribe(id)
    }

    @GetMapping("/{id}/sections")
    fun sections(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID): List<SectionResponse> =
        queryService.sections(principal.workspaceId, id).map { it.toResponse() }

    @PatchMapping("/{id}/sections/{sectionId}")
    fun excludeSection(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @PathVariable id: UUID,
        @PathVariable sectionId: UUID,
        @RequestBody request: ExcludeSectionRequest,
    ): SectionResponse =
        queryService.excludeSection(principal.workspaceId, id, sectionId, request.excluded).toResponse()

    @PostMapping("/{id}/ocr")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun triggerOcr(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID): OcrTriggerResponse =
        OcrTriggerResponse(ocrTriggerService.trigger(principal.workspaceId, id))

    private fun Source.toResponse() = SourceResponse(
        id = id,
        kind = kind.name,
        title = title,
        status = status.name,
        failureReason = failureReason,
        pageCount = pageCount,
        needsOcr = needsOcr,
        createdAt = createdAt,
    )

    private fun Section.toResponse() =
        SectionResponse(id = id, parentId = parentId, ordinal = ordinal, level = level, title = title, excluded = excluded)
}
