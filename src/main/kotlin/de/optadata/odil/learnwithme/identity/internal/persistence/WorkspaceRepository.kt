package de.optadata.odil.learnwithme.identity.internal.persistence

import de.optadata.odil.learnwithme.identity.internal.domain.Workspace
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkspaceRepository : JpaRepository<Workspace, UUID> {
    fun findByOwnerId(ownerId: UUID): Workspace?
}
