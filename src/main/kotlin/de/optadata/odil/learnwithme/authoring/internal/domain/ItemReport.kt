package de.optadata.odil.learnwithme.authoring.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** C5: 1-Klick-Report einer schlechten Frage. */
@Entity
@Table(name = "item_reports")
class ItemReport(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "item_id", nullable = false)
    val itemId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val reason: String,

    var comment: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
