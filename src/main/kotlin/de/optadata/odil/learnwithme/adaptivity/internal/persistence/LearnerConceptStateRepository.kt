package de.optadata.odil.learnwithme.adaptivity.internal.persistence

import de.optadata.odil.learnwithme.adaptivity.internal.domain.LearnerConceptState
import de.optadata.odil.learnwithme.adaptivity.internal.domain.LearnerConceptStateId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface LearnerConceptStateRepository : JpaRepository<LearnerConceptState, LearnerConceptStateId> {

    // `Id` in der Mitte adressiert die `@EmbeddedId`-Pfade `id.userId`/`id.conceptId` — `userId`/`conceptId`
    // sind keine eigenen JPA-Attribute auf der Entity (nur Kotlin-Convenience-Getter, siehe [LearnerConceptState]).
    fun findAllByWorkspaceIdAndIdUserIdAndIdConceptIdIn(workspaceId: UUID, userId: UUID, conceptIds: List<UUID>): List<LearnerConceptState>

    fun findAllByWorkspaceIdAndIdUserId(workspaceId: UUID, userId: UUID): List<LearnerConceptState>

    @Query(
        "SELECT s FROM LearnerConceptState s WHERE s.workspaceId = :workspaceId AND s.id.userId = :userId " +
            "AND s.dueAt IS NOT NULL AND s.dueAt <= :now ORDER BY s.dueAt ASC",
    )
    fun findDue(@Param("workspaceId") workspaceId: UUID, @Param("userId") userId: UUID, @Param("now") now: Instant): List<LearnerConceptState>
}
