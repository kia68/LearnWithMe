package de.optadata.odil.learnwithme.ai.internal.persistence

import de.optadata.odil.learnwithme.ai.internal.domain.AiCredential
import de.optadata.odil.learnwithme.ai.internal.domain.AiProvider
import de.optadata.odil.learnwithme.ai.internal.domain.CredentialStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AiCredentialRepository : JpaRepository<AiCredential, UUID> {
    fun findAllByWorkspaceId(workspaceId: UUID): List<AiCredential>
    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): AiCredential?
    fun existsByWorkspaceIdAndStatus(workspaceId: UUID, status: CredentialStatus): Boolean
    fun findFirstByWorkspaceIdAndProviderAndStatus(workspaceId: UUID, provider: AiProvider, status: CredentialStatus): AiCredential?
}
