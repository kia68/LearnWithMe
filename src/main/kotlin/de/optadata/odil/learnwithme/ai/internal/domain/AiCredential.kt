package de.optadata.odil.learnwithme.ai.internal.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * BYOK-Credential (A3). Der Klartext-API-Key verlässt [de.optadata.odil.learnwithme.ai.internal.crypto.EnvelopeEncryptionService]
 * nie — hier liegen ausschließlich Ciphertext, gewrapptes DEK und Nonce (ADR-009).
 */
@Entity
@Table(name = "ai_credentials")
class AiCredential(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: AiProvider,

    var label: String? = null,

    @Column(nullable = false)
    var ciphertext: ByteArray,

    @Column(name = "wrapped_dek", nullable = false)
    var wrappedDek: ByteArray,

    @Column(nullable = false)
    var nonce: ByteArray,

    @Column(name = "key_hint", nullable = false)
    var keyHint: String,

    @Column(name = "base_url")
    var baseUrl: String? = null,

    var region: String? = null,

    @Column(name = "last_verified_at")
    var lastVerifiedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CredentialStatus = CredentialStatus.UNVERIFIED,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
