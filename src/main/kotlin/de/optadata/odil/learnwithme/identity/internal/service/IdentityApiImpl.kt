package de.optadata.odil.learnwithme.identity.internal.service

import de.optadata.odil.learnwithme.identity.IdentityApi
import de.optadata.odil.learnwithme.identity.internal.persistence.UserRepository
import de.optadata.odil.learnwithme.identity.internal.persistence.WorkspaceRepository
import de.optadata.odil.learnwithme.shared.web.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class IdentityApiImpl(
    private val workspaceRepository: WorkspaceRepository,
    private val userRepository: UserRepository,
) : IdentityApi {

    override fun getPlan(workspaceId: UUID): String {
        val workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow { NotFoundException("Workspace $workspaceId nicht gefunden") }
        val owner = userRepository.findById(workspace.ownerId)
            .orElseThrow { NotFoundException("Owner von Workspace $workspaceId nicht gefunden") }
        return owner.plan.name
    }
}
