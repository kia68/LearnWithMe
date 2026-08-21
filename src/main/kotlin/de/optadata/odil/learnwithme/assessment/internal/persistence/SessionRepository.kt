package de.optadata.odil.learnwithme.assessment.internal.persistence

import de.optadata.odil.learnwithme.assessment.internal.domain.Session
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SessionRepository : JpaRepository<Session, UUID> {
    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Session?
}
