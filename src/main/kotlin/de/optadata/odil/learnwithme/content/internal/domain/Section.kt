package de.optadata.odil.learnwithme.content.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** Ein Strukturknoten (Kapitel/Abschnitt) eines Dokuments (B6). Baum über [parentId]. */
@Entity
@Table(name = "sections")
class Section(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "source_id", nullable = false)
    val sourceId: UUID,

    @Column(name = "parent_id")
    var parentId: UUID? = null,

    @Column(nullable = false)
    var ordinal: Int,

    @Column(nullable = false)
    var level: Int,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var excluded: Boolean = false,
)
