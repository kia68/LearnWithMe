package de.optadata.odil.learnwithme.authoring.internal.generation

import de.optadata.odil.learnwithme.ai.LlmGateway
import de.optadata.odil.learnwithme.ai.LlmTask
import de.optadata.odil.learnwithme.ai.StructuredRequest
import de.optadata.odil.learnwithme.authoring.internal.domain.BloomLevel
import de.optadata.odil.learnwithme.authoring.internal.domain.ClozePayload
import de.optadata.odil.learnwithme.authoring.internal.domain.Item
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemStatus
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemType
import de.optadata.odil.learnwithme.authoring.internal.domain.MatchingPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.McMultiPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.McSinglePayload
import de.optadata.odil.learnwithme.authoring.internal.domain.OrderingPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.PayloadCodec
import de.optadata.odil.learnwithme.authoring.internal.domain.TrueFalsePayload
import de.optadata.odil.learnwithme.authoring.internal.persistence.ItemRepository
import de.optadata.odil.learnwithme.authoring.internal.quality.DuplicateGate
import de.optadata.odil.learnwithme.authoring.internal.quality.GroundednessGate
import de.optadata.odil.learnwithme.authoring.internal.quality.PgVectorFormat
import de.optadata.odil.learnwithme.authoring.internal.quality.StructuralGate
import de.optadata.odil.learnwithme.content.ContentApi
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import de.optadata.odil.learnwithme.shared.JsonMapper
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Kern-Flow von C1: Draft generieren → Strukturgate (C3) → Groundedness-Gate (C2/ADR-008) →
 * Duplikat-Gate (C4) → persistieren. Jedes Item bekommt genau einen zitierten Chunk als Beleg
 * (ADR-008: „LLM-Judge mit *nur* dem zitierten Chunk als Kontext"), Belegumfang ist der ganze
 * zitierte Chunk (kein Sub-Zitat-Tracking — die Chunks sind bereits klein genug, ~512 Tokens).
 */
@Component
class GenerationPipeline(
    private val knowledgeApi: KnowledgeApi,
    private val contentApi: ContentApi,
    private val llmGateway: LlmGateway,
    private val structuralGate: StructuralGate,
    private val groundednessGate: GroundednessGate,
    private val duplicateGate: DuplicateGate,
    private val itemRepository: ItemRepository,
) {
    private val objectMapper = JsonMapper.instance

    fun generateOne(workspaceId: UUID, conceptId: UUID, type: ItemType, evidenceChunkId: UUID): Item {
        val concept = knowledgeApi.getConcept(conceptId)
        val chunk = contentApi.getChunk(evidenceChunkId)
            ?: throw NotFoundException("Chunk $evidenceChunkId nicht gefunden")

        val (draft, model) = requestDraft(workspaceId, type, concept.name, concept.summary, chunk.text)

        val structuralIssues = structuralGate.check(draft.payload)
        if (structuralIssues.isNotEmpty()) {
            return persist(
                workspaceId, conceptId, type, draft, chunk.id, chunk.charFrom, chunk.charTo, model,
                status = ItemStatus.REJECTED,
                quality = mapOf("rejectionReason" to "STRUCTURAL", "issues" to structuralIssues.map { it.message }),
            )
        }

        val groundedness = groundednessGate.check(workspaceId, draft.stem, draft.explanation, chunk.text)
        if (!groundedness.grounded) {
            return persist(
                workspaceId, conceptId, type, draft, chunk.id, chunk.charFrom, chunk.charTo, model,
                status = ItemStatus.REJECTED,
                quality = mapOf(
                    "rejectionReason" to "UNGROUNDED",
                    "similarity" to groundedness.similarity,
                    "judgeReason" to groundedness.judgeReason,
                ),
            )
        }

        val duplicate = duplicateGate.check(workspaceId, conceptId, draft.stem)
        if (duplicate.isDuplicate) {
            return persist(
                workspaceId, conceptId, type, draft, chunk.id, chunk.charFrom, chunk.charTo, model,
                status = ItemStatus.REJECTED,
                quality = mapOf("rejectionReason" to "DUPLICATE", "duplicateOfItemId" to duplicate.duplicateOfItemId.toString()),
            )
        }

        val item = persist(
            workspaceId, conceptId, type, draft, chunk.id, chunk.charFrom, chunk.charTo, model,
            status = ItemStatus.DRAFT,
            quality = mapOf("similarity" to groundedness.similarity, "judgeReason" to groundedness.judgeReason),
        )
        itemRepository.updateEmbedding(item.id, PgVectorFormat.toLiteral(duplicate.embedding))
        return item
    }

    private data class DraftResult(
        val stem: String,
        val explanation: String,
        val bloomLevel: BloomLevel,
        val payload: ItemPayload,
    )

    private fun requestDraft(
        workspaceId: UUID,
        type: ItemType,
        conceptName: String,
        conceptSummary: String,
        chunkText: String,
    ): Pair<DraftResult, String> {
        val system = PromptBuilder.systemPrompt(type)
        val user = PromptBuilder.userPrompt(conceptName, conceptSummary, chunkText)

        return when (type) {
            ItemType.MC_SINGLE -> {
                val result = llmGateway.complete(StructuredRequest(workspaceId, LlmTask.ITEM_GENERATION, system, user, McSingleDraft::class.java))
                val d = result.value
                DraftResult(d.stem, d.explanation, d.bloomLevel, McSinglePayload(d.options)) to result.model
            }
            ItemType.MC_MULTI -> {
                val result = llmGateway.complete(StructuredRequest(workspaceId, LlmTask.ITEM_GENERATION, system, user, McMultiDraft::class.java))
                val d = result.value
                DraftResult(d.stem, d.explanation, d.bloomLevel, McMultiPayload(d.options)) to result.model
            }
            ItemType.TRUE_FALSE -> {
                val result = llmGateway.complete(StructuredRequest(workspaceId, LlmTask.ITEM_GENERATION, system, user, TrueFalseDraft::class.java))
                val d = result.value
                DraftResult(d.stem, d.explanation, d.bloomLevel, TrueFalsePayload(d.statement, d.answer, d.rationale)) to result.model
            }
            ItemType.ORDERING -> {
                val result = llmGateway.complete(StructuredRequest(workspaceId, LlmTask.ITEM_GENERATION, system, user, OrderingDraft::class.java))
                val d = result.value
                DraftResult(d.stem, d.explanation, d.bloomLevel, OrderingPayload(d.elements, d.correctOrder)) to result.model
            }
            ItemType.MATCHING -> {
                val result = llmGateway.complete(StructuredRequest(workspaceId, LlmTask.ITEM_GENERATION, system, user, MatchingDraft::class.java))
                val d = result.value
                DraftResult(d.stem, d.explanation, d.bloomLevel, MatchingPayload(d.left, d.right, d.pairs, d.distractorsRight)) to result.model
            }
            ItemType.CLOZE -> {
                val result = llmGateway.complete(StructuredRequest(workspaceId, LlmTask.ITEM_GENERATION, system, user, ClozeDraft::class.java))
                val d = result.value
                DraftResult(d.stem, d.explanation, d.bloomLevel, ClozePayload(d.template, d.blanks)) to result.model
            }
        }
    }

    private fun persist(
        workspaceId: UUID,
        conceptId: UUID,
        type: ItemType,
        draft: DraftResult,
        sourceChunkId: UUID,
        sourceCharFrom: Int,
        sourceCharTo: Int,
        model: String,
        status: ItemStatus,
        quality: Map<String, Any?>,
    ): Item = itemRepository.save(
        Item(
            workspaceId = workspaceId,
            conceptId = conceptId,
            type = type,
            stem = draft.stem,
            payload = PayloadCodec.serialize(draft.payload),
            explanation = draft.explanation,
            bloomLevel = draft.bloomLevel,
            sourceChunkId = sourceChunkId,
            sourceCharFrom = sourceCharFrom,
            sourceCharTo = sourceCharTo,
            status = status,
            quality = objectMapper.writeValueAsString(quality),
            generatedBy = model,
        ),
    )
}
