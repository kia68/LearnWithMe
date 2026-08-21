package de.optadata.odil.learnwithme.analytics.internal.persistence

import de.optadata.odil.learnwithme.analytics.internal.domain.ErrorCategory
import de.optadata.odil.learnwithme.analytics.internal.domain.Misconception
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MisconceptionRepository : JpaRepository<Misconception, UUID> {

    fun findByUserIdAndConceptIdAndCategory(userId: UUID, conceptId: UUID, category: ErrorCategory): Misconception?

    fun findAllByWorkspaceIdAndUserIdOrderByLastSeenAtDesc(workspaceId: UUID, userId: UUID): List<Misconception>
}
