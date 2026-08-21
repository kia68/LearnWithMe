package de.optadata.odil.learnwithme.identity.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Opakes, serverseitig widerrufbares Refresh-Token (A1: 30 Tage gültig). Es wird
 * nur der SHA-256-Hash gespeichert — das Rohtoken existiert ausschließlich beim
 * Client. Rotation bei jedem `/auth/refresh`-Aufruf über [replacedById].
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "replaced_by_id")
    var replacedById: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    fun isActive(now: Instant): Boolean = revokedAt == null && expiresAt.isAfter(now)
}
