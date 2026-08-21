package de.optadata.odil.learnwithme.content.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Ein Textabschnitt mit exakten Zeichen-Offsets im Volltext (P1: belegbares Zitat).
 * `embedding` (`vector(1536)`, siehe V3-Migration) wird bewusst nicht gemappt — befüllt
 * wird die Spalte erst vom `LlmGateway` (M1); bis dahin bleibt sie NULL.
 */
@Entity
@Table(name = "chunks")
class Chunk(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "source_id", nullable = false)
    val sourceId: UUID,

    @Column(name = "section_id")
    var sectionId: UUID? = null,

    @Column(nullable = false)
    var ordinal: Int,

    @Column(nullable = false)
    var text: String,

    @Column(name = "token_count", nullable = false)
    var tokenCount: Int,

    @Column(name = "page_from")
    var pageFrom: Int? = null,

    @Column(name = "page_to")
    var pageTo: Int? = null,

    @Column(name = "char_from", nullable = false)
    var charFrom: Int,

    @Column(name = "char_to", nullable = false)
    var charTo: Int,
)
