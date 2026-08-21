package de.optadata.odil.learnwithme.ai.internal.llm

import de.optadata.odil.learnwithme.ai.internal.crypto.Envelope
import de.optadata.odil.learnwithme.ai.internal.crypto.EnvelopeEncryptionService
import de.optadata.odil.learnwithme.ai.internal.domain.AiProvider
import de.optadata.odil.learnwithme.ai.internal.domain.CredentialStatus
import de.optadata.odil.learnwithme.ai.internal.persistence.AiCredentialRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

data class ResolvedKey(val apiKey: String, val baseUrl: String?, val isByok: Boolean)

/** BYOK-first Key-Auflösung (A3/A6): ein verifiziertes eigenes Credential geht immer vor dem
 * Plattform-Key. Der Klartext existiert nur für die Dauer dieses Aufrufs im Speicher (ADR-009). */
@Component
class CredentialResolver(
    private val credentialRepository: AiCredentialRepository,
    private val envelopeEncryptionService: EnvelopeEncryptionService,
    @Value("\${spring.ai.openai.api-key:}") private val platformOpenAiKey: String,
) {

    fun resolveOpenAiKey(workspaceId: UUID): ResolvedKey {
        val byok = credentialRepository.findFirstByWorkspaceIdAndProviderAndStatus(
            workspaceId,
            AiProvider.OPENAI,
            CredentialStatus.VERIFIED,
        )
        if (byok != null) {
            val plaintext = envelopeEncryptionService.decrypt(Envelope(byok.ciphertext, byok.wrappedDek, byok.nonce))
            return ResolvedKey(plaintext, byok.baseUrl, isByok = true)
        }
        require(platformOpenAiKey.isNotBlank()) {
            "Kein Plattform-OpenAI-Key konfiguriert (OPENAI_API_KEY) und kein verifiziertes BYOK-Credential vorhanden."
        }
        return ResolvedKey(platformOpenAiKey, null, isByok = false)
    }
}
