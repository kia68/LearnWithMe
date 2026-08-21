package de.optadata.odil.learnwithme.knowledge.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** Eine Belegstelle (Chunk) für ein [Concept] (B8). */
@Entity
@Table(name = "concept_evidence")
class ConceptEvidence(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "concept_id", nullable = false)
    val conceptId: UUID,

    @Column(name = "chunk_id", nullable = false)
    val chunkId: UUID,

    @Column(nullable = false)
    var weight: Float = 1.0f,
)
