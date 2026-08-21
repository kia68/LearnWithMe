package de.optadata.odil.learnwithme.identity.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Audit-Nachweis der DSGVO-Löschung (A5). Bewusst ohne FK auf `users`, da der
 * User zum Zeitpunkt des Eintrags bereits gelöscht ist. */
@Entity
@Table(name = "account_deletions")
class AccountDeletionRecord(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val email: String,

    @Column(name = "requested_at", nullable = false)
    val requestedAt: Instant = Instant.now(),

    @Column(name = "completed_at", nullable = false)
    val completedAt: Instant = Instant.now(),
)
