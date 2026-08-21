package de.optadata.odil.learnwithme.ai.internal.persistence

import de.optadata.odil.learnwithme.ai.internal.domain.LlmUsageRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface LlmUsageRepository : JpaRepository<LlmUsageRecord, Long> {

    fun findAllByWorkspaceIdAndCreatedAtBetween(workspaceId: UUID, from: Instant, to: Instant): List<LlmUsageRecord>

    @Query(
        "select coalesce(sum(u.costMicros), 0) from LlmUsageRecord u " +
            "where u.workspaceId = :workspaceId and u.createdAt >= :since",
    )
    fun sumCostMicrosSince(@Param("workspaceId") workspaceId: UUID, @Param("since") since: Instant): Long
}
