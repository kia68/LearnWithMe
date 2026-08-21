package de.optadata.odil.learnwithme.identity.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    /** Uniqueness wird per funktionalem Index `users_email_lower_uidx` auf `lower(email)`
     * erzwungen (V1-Migration) — Normalisierung passiert in [de.optadata.odil.learnwithme.identity.internal.service.AuthService]. */
    @Column(nullable = false)
    var email: String,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(nullable = false)
    var locale: String = "de",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var plan: Plan = Plan.FREE,

    /** Null bei Konten, die ausschließlich per SSO angelegt wurden (A1). */
    @Column(name = "password_hash")
    var passwordHash: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}
