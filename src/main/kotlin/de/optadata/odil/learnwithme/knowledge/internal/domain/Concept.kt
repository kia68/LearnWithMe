package de.optadata.odil.learnwithme.knowledge.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Ein Kernkonzept eines Dokuments (B8). `embedding` (`vector(1536)`, siehe V4-Migration)
 * wird bewusst nicht gemappt — befüllt erst vom `LlmGateway` (M1), siehe [de.optadata.odil.learnwithme.content.internal.domain.Chunk]. */
@Entity
@Table(name = "concepts")
class Concept(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "source_id")
    val sourceId: UUID?,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var summary: String,

    @Column(nullable = false)
    var frequency: Int = 1,

    @Column(nullable = false)
    var importance: Float = 0.5f,

    @Column(name = "item_target", nullable = false)
    var itemTarget: Int = 5,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
