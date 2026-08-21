package de.optadata.odil.learnwithme.authoring.internal.web.dto

import de.optadata.odil.learnwithme.authoring.internal.domain.ItemPayload
import java.time.Instant
import java.util.UUID

data class GenerateItemsRequest(val count: Int, val types: List<String> = emptyList())

data class GenerateItemsResponse(val jobId: UUID)

data class ItemResponse(
    val id: UUID,
    val conceptId: UUID,
    val type: String,
    val stem: String,
    val payload: ItemPayload,
    val explanation: String,
    val bloomLevel: String,
    val status: String,
    val sourceChunkId: UUID,
    val sourceCharFrom: Int,
    val sourceCharTo: Int,
    val reportCount: Int,
    val generatedBy: String?,
    val createdAt: Instant,
)

data class ItemPageResponse(val items: List<ItemResponse>, val page: Int, val size: Int, val totalElements: Long)

data class ReportItemRequest(val reason: String, val comment: String? = null)

data class BulkItemIdsRequest(val ids: List<UUID>)
