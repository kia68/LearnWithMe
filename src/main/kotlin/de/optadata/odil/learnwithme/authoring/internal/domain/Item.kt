package de.optadata.odil.learnwithme.authoring.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Eine generierte Frage (Epic C). `payload`/`quality` sind Jackson-JSON-TEXT (siehe V5-Migration).
 * `embedding` (`vector(1536)`, Duplikaterkennung C4) ist bewusst nicht gemappt — Ähnlichkeitsabfragen
 * laufen über native Queries in [de.optadata.odil.learnwithme.authoring.internal.persistence.ItemRepository]. */
@Entity
@Table(name = "items")
class Item(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "concept_id", nullable = false)
    val conceptId: UUID,

    @Column(name = "parent_item_id")
    var parentItemId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ItemType,

    @Column(nullable = false)
    var stem: String,

    @Column(nullable = false)
    var payload: String,

    @Column(nullable = false)
    var explanation: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", nullable = false)
    var bloomLevel: BloomLevel,

    @Column(nullable = false)
    var language: String = "de",

    @Column(name = "source_chunk_id", nullable = false)
    val sourceChunkId: UUID,

    @Column(name = "source_char_from", nullable = false)
    val sourceCharFrom: Int,

    @Column(name = "source_char_to", nullable = false)
    val sourceCharTo: Int,

    @Column(nullable = false)
    var difficulty: Float = 0.0f,

    @Column(name = "difficulty_n", nullable = false)
    var difficultyN: Int = 0,

    @Column(name = "p_correct")
    var pCorrect: Float? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ItemStatus = ItemStatus.DRAFT,

    @Column(nullable = false)
    var quality: String = "{}",

    @Column(name = "report_count", nullable = false)
    var reportCount: Int = 0,

    @Column(name = "skip_count", nullable = false)
    var skipCount: Int = 0,

    @Column(name = "generated_by")
    var generatedBy: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
