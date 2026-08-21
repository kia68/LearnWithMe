package de.optadata.odil.learnwithme.ai.internal.service

import de.optadata.odil.learnwithme.ai.internal.crypto.Envelope
import de.optadata.odil.learnwithme.ai.internal.crypto.EnvelopeEncryptionService
import de.optadata.odil.learnwithme.ai.internal.domain.AiCredential
import de.optadata.odil.learnwithme.ai.internal.domain.CredentialStatus
import de.optadata.odil.learnwithme.ai.internal.persistence.AiCredentialRepository
import de.optadata.odil.learnwithme.ai.internal.verification.CredentialVerifier
import de.optadata.odil.learnwithme.ai.internal.web.dto.CreateCredentialRequest
import de.optadata.odil.learnwithme.ai.internal.web.dto.CredentialResponse
import de.optadata.odil.learnwithme.ai.internal.web.dto.VerifyCredentialResponse
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** A3: BYOK-Credentials verwalten. Der Klartext-Key wird nach dem Verschlüsseln
 * sofort verworfen und existiert danach nur noch für die Dauer eines Verify-Aufrufs
 * im Speicher (ADR-009) — kein API-Pfad gibt ihn je zurück. */
@Service
class AiCredentialService(
    private val repository: AiCredentialRepository,
    private val envelopeEncryptionService: EnvelopeEncryptionService,
    private val credentialVerifier: CredentialVerifier,
) {

    fun list(workspaceId: UUID): List<CredentialResponse> =
        repository.findAllByWorkspaceId(workspaceId).map { it.toResponse() }

    @Transactional
    fun create(workspaceId: UUID, request: CreateCredentialRequest): CredentialResponse {
        val envelope = envelopeEncryptionService.encrypt(request.apiKey)
        val credential = repository.save(
            AiCredential(
                workspaceId = workspaceId,
                provider = request.provider,
                label = request.label,
                ciphertext = envelope.ciphertext,
                wrappedDek = envelope.wrappedDek,
                nonce = envelope.nonce,
                keyHint = keyHintOf(request.apiKey),
                baseUrl = request.baseUrl,
            ),
        )
        return credential.toResponse()
    }

    @Transactional
    fun verify(workspaceId: UUID, credentialId: UUID): VerifyCredentialResponse {
        val credential = requireOwned(workspaceId, credentialId)
        val plaintextKey = envelopeEncryptionService.decrypt(
            Envelope(credential.ciphertext, credential.wrappedDek, credential.nonce),
        )
        val result = credentialVerifier.verify(credential.provider, plaintextKey, credential.baseUrl)

        credential.status = if (result.success) CredentialStatus.VERIFIED else CredentialStatus.INVALID
        credential.lastVerifiedAt = Instant.now()
        repository.save(credential)

        return VerifyCredentialResponse(credential.status.name, result.message, credential.lastVerifiedAt)
    }

    @Transactional
    fun delete(workspaceId: UUID, credentialId: UUID) {
        val credential = requireOwned(workspaceId, credentialId)
        repository.delete(credential)
    }

    private fun requireOwned(workspaceId: UUID, credentialId: UUID): AiCredential =
        repository.findByIdAndWorkspaceId(credentialId, workspaceId)
            ?: throw NotFoundException("AI-Credential $credentialId nicht gefunden")

    private fun keyHintOf(apiKey: String): String = "…" + apiKey.takeLast(4)

    private fun AiCredential.toResponse() = CredentialResponse(
        id = id,
        provider = provider,
        label = label,
        keyHint = keyHint,
        baseUrl = baseUrl,
        region = region,
        status = status.name,
        lastVerifiedAt = lastVerifiedAt,
        createdAt = createdAt,
    )
}
