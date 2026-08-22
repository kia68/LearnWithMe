package de.optadata.odil.learnwithme.platform.internal.job

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface JobRepository : JpaRepository<JobEntity, UUID> {

    fun findByJobKey(jobKey: String): JobEntity?

    /** Epic-H-Härtung: der Poll-Endpunkt für `GRADE_FREE_TEXT` kennt nur `sessionId`/`itemId`,
     * nicht die volle `jobKey` (die trägt zusätzlich einen Enqueue-Zeitstempel, siehe
     * `AttemptService.submit`) — Prefix-Suche auf den gemeinsamen Teil, neuester zuerst, falls
     * (theoretisch, `enqueue` ist idempotent über den vollen Key) mehrere existieren. */
    fun findFirstByWorkspaceIdAndJobKeyStartingWithOrderByCreatedAtDesc(workspaceId: UUID, jobKeyPrefix: String): JobEntity?

    /** ADR-012: `SKIP LOCKED` lässt mehrere Worker-Instanzen sicher um Jobs konkurrieren —
     * muss innerhalb einer Transaktion aufgerufen werden, die den Lock bis zum
     * Status-Update auf `RUNNING` hält (siehe [JobClaimer]). */
    @Query(
        value = "SELECT * FROM jobs WHERE status = 'PENDING' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun lockNextPending(): JobEntity?
}
