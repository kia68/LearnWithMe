package de.optadata.odil.learnwithme.identity.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Eine mit einem [User] verknüpfte SSO-Identität (A1: Google/GitHub). */
@Entity
@Table(name = "linked_identities")
class LinkedIdentity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: SsoProvider,

    @Column(name = "provider_uid", nullable = false)
    val providerUid: String,

    @Column(name = "linked_at", nullable = false, updatable = false)
    val linkedAt: Instant = Instant.now(),
)
