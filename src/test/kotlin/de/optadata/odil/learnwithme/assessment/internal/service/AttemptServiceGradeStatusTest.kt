package de.optadata.odil.learnwithme.assessment.internal.service

import de.optadata.odil.learnwithme.adaptivity.AdaptivityApi
import de.optadata.odil.learnwithme.analytics.AnalyticsApi
import de.optadata.odil.learnwithme.assessment.internal.domain.Session
import de.optadata.odil.learnwithme.assessment.internal.domain.SessionGoalKind
import de.optadata.odil.learnwithme.assessment.internal.domain.SessionScopeKind
import de.optadata.odil.learnwithme.assessment.internal.grading.ResponseGrader
import de.optadata.odil.learnwithme.assessment.internal.persistence.AttemptRepository
import de.optadata.odil.learnwithme.assessment.internal.persistence.SessionRepository
import de.optadata.odil.learnwithme.assessment.internal.selection.ItemSelectionService
import de.optadata.odil.learnwithme.authoring.AuthoringApi
import de.optadata.odil.learnwithme.content.ContentApi
import de.optadata.odil.learnwithme.platform.JobOutcome
import de.optadata.odil.learnwithme.platform.JobQueue
import de.optadata.odil.learnwithme.platform.JobStatusView
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** Härtung (siehe `docs/progress.md` „Bekannte Lücken" Epic H): scheitert `GRADE_FREE_TEXT`
 * endgültig, darf der Poll-Endpunkt nicht für immer bei [GradeStatus.Pending] hängen bleiben. */
class AttemptServiceGradeStatusTest {

    private val sessionRepository = mockk<SessionRepository>()
    private val attemptRepository = mockk<AttemptRepository>()
    private val jobQueue = mockk<JobQueue>()

    private val service = AttemptService(
        sessionRepository = sessionRepository,
        attemptRepository = attemptRepository,
        authoringApi = mockk<AuthoringApi>(),
        adaptivityApi = mockk<AdaptivityApi>(),
        contentApi = mockk<ContentApi>(),
        grader = mockk<ResponseGrader>(),
        selectionService = mockk<ItemSelectionService>(),
        analyticsApi = mockk<AnalyticsApi>(),
        jobQueue = jobQueue,
    )

    private val workspaceId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()

    private fun stubSessionAndNoAttempt() {
        val session = Session(
            id = sessionId, workspaceId = workspaceId, userId = userId,
            scopeKind = SessionScopeKind.MIXED, goalKind = SessionGoalKind.ITEM_COUNT, goalValue = 10,
        )
        every { sessionRepository.findByIdAndWorkspaceId(sessionId, workspaceId) } returns session
        every { attemptRepository.findFirstBySessionIdAndItemIdOrderByCreatedAtDesc(sessionId, itemId) } returns null
    }

    @Test
    fun `no attempt row and a FAILED job yields GradeStatus Failed with the last error`() {
        stubSessionAndNoAttempt()
        every { jobQueue.statusByKeyPrefix(workspaceId, "grade:$sessionId:$itemId:") } returns
            JobStatusView(JobOutcome.FAILED, "Kein Plattform-OpenAI-Key konfiguriert")

        val status = service.gradeStatus(workspaceId, sessionId, itemId)

        assertTrue(status is GradeStatus.Failed)
        assertEquals("Kein Plattform-OpenAI-Key konfiguriert", (status as GradeStatus.Failed).lastError)
    }

    @Test
    fun `no attempt row and a still-running job yields GradeStatus Pending`() {
        stubSessionAndNoAttempt()
        every { jobQueue.statusByKeyPrefix(workspaceId, "grade:$sessionId:$itemId:") } returns JobStatusView(JobOutcome.PENDING, null)

        assertEquals(GradeStatus.Pending, service.gradeStatus(workspaceId, sessionId, itemId))
    }

    @Test
    fun `no attempt row and no matching job yields GradeStatus Pending`() {
        stubSessionAndNoAttempt()
        every { jobQueue.statusByKeyPrefix(workspaceId, "grade:$sessionId:$itemId:") } returns null

        assertEquals(GradeStatus.Pending, service.gradeStatus(workspaceId, sessionId, itemId))
    }
}
