package de.optadata.odil.learnwithme.authoring.internal.web

import de.optadata.odil.learnwithme.authoring.internal.domain.Item
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemStatus
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemType
import de.optadata.odil.learnwithme.authoring.internal.domain.PayloadCodec
import de.optadata.odil.learnwithme.authoring.internal.service.ItemGenerationTriggerService
import de.optadata.odil.learnwithme.authoring.internal.service.ItemQueryService
import de.optadata.odil.learnwithme.authoring.internal.service.ItemReportService
import de.optadata.odil.learnwithme.authoring.internal.service.ItemReviewService
import de.optadata.odil.learnwithme.authoring.internal.web.dto.BulkItemIdsRequest
import de.optadata.odil.learnwithme.authoring.internal.web.dto.GenerateItemsRequest
import de.optadata.odil.learnwithme.authoring.internal.web.dto.GenerateItemsResponse
import de.optadata.odil.learnwithme.authoring.internal.web.dto.ItemPageResponse
import de.optadata.odil.learnwithme.authoring.internal.web.dto.ItemResponse
import de.optadata.odil.learnwithme.authoring.internal.web.dto.ReportItemRequest
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** C1-C8: Fragegenerierung, Qualitätstore, Report, Review-Queue. */
@RestController
class ItemController(
    private val triggerService: ItemGenerationTriggerService,
    private val queryService: ItemQueryService,
    private val reviewService: ItemReviewService,
    private val reportService: ItemReportService,
) {

    @PostMapping("/api/v1/concepts/{conceptId}/items:generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun generate(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @PathVariable conceptId: UUID,
        @RequestBody request: GenerateItemsRequest,
    ): GenerateItemsResponse {
        val types = request.types.map { ItemType.valueOf(it) }
        val jobId = triggerService.trigger(principal.workspaceId, conceptId, request.count, types)
        return GenerateItemsResponse(jobId)
    }

    @GetMapping("/api/v1/concepts/{conceptId}/items")
    fun listForConcept(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable conceptId: UUID): List<ItemResponse> =
        queryService.listForConcept(principal.workspaceId, conceptId).map { it.toResponse() }

    @GetMapping("/api/v1/items/review-queue")
    fun reviewQueue(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @RequestParam(defaultValue = "DRAFT") status: ItemStatus,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ItemPageResponse {
        val result = queryService.reviewQueue(principal.workspaceId, status, page, size)
        return ItemPageResponse(result.content.map { it.toResponse() }, page, size, result.totalElements)
    }

    @PostMapping("/api/v1/items/{id}/publish")
    fun publish(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID): ItemResponse =
        reviewService.publish(principal.workspaceId, id).toResponse()

    @PostMapping("/api/v1/items/{id}/reject")
    fun reject(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID): ItemResponse =
        reviewService.reject(principal.workspaceId, id).toResponse()

    @PostMapping("/api/v1/items:bulk-publish")
    fun bulkPublish(@AuthenticationPrincipal principal: TenantPrincipal, @RequestBody request: BulkItemIdsRequest): List<ItemResponse> =
        reviewService.bulkPublish(principal.workspaceId, request.ids).map { it.toResponse() }

    @PostMapping("/api/v1/items:bulk-reject")
    fun bulkReject(@AuthenticationPrincipal principal: TenantPrincipal, @RequestBody request: BulkItemIdsRequest): List<ItemResponse> =
        reviewService.bulkReject(principal.workspaceId, request.ids).map { it.toResponse() }

    @PostMapping("/api/v1/items/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun report(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @PathVariable id: UUID,
        @RequestBody request: ReportItemRequest,
    ) {
        reportService.report(principal.workspaceId, principal.userId, id, request.reason, request.comment)
    }

    private fun Item.toResponse() = ItemResponse(
        id = id,
        conceptId = conceptId,
        type = type.name,
        stem = stem,
        payload = PayloadCodec.deserialize(type, payload),
        explanation = explanation,
        bloomLevel = bloomLevel.name,
        status = status.name,
        sourceChunkId = sourceChunkId,
        sourceCharFrom = sourceCharFrom,
        sourceCharTo = sourceCharTo,
        reportCount = reportCount,
        generatedBy = generatedBy,
        createdAt = createdAt,
    )
}
