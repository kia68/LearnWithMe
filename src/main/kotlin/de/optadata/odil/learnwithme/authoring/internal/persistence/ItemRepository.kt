package de.optadata.odil.learnwithme.authoring.internal.persistence

import de.optadata.odil.learnwithme.authoring.internal.domain.Item
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ItemRepository : JpaRepository<Item, UUID> {

    fun findAllByConceptIdOrderByCreatedAtDesc(conceptId: UUID): List<Item>

    fun findAllByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId: UUID, status: ItemStatus, pageable: Pageable): Page<Item>

    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Item?

    fun countByConceptIdAndStatusNot(conceptId: UUID, status: ItemStatus): Long

    /** C4: Duplikaterkennung per pgvector-Kosinus-Distanz, gescopet auf dasselbe Dokument
     * (über `concepts.source_id`). Bewusst native statt gemapptes `embedding`-Feld —
     * siehe [Item]-Kommentar. Vektor wird als pgvector-Textliteral `"[0.1,0.2,...]"` übergeben. */
    @Query(
        value = """
            SELECT i.id FROM items i
            JOIN concepts c ON c.id = i.concept_id
            WHERE c.source_id = (SELECT source_id FROM concepts WHERE id = :conceptId)
              AND i.embedding IS NOT NULL
              AND i.status <> 'REJECTED'
              AND 1 - (i.embedding <=> CAST(:vector AS vector)) > :threshold
            ORDER BY i.embedding <=> CAST(:vector AS vector)
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findNearestDuplicateId(
        @Param("conceptId") conceptId: UUID,
        @Param("vector") vectorLiteral: String,
        @Param("threshold") threshold: Double,
    ): UUID?

    @Modifying
    @Query(value = "UPDATE items SET embedding = CAST(:vector AS vector) WHERE id = :id", nativeQuery = true)
    fun updateEmbedding(@Param("id") id: UUID, @Param("vector") vectorLiteral: String)
}
