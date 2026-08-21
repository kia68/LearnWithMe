package de.optadata.odil.learnwithme.assessment.internal.service

import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome
import de.optadata.odil.learnwithme.assessment.internal.domain.Session
import de.optadata.odil.learnwithme.assessment.internal.domain.SessionGoalKind
import de.optadata.odil.learnwithme.assessment.internal.domain.SessionScopeKind
import de.optadata.odil.learnwithme.assessment.internal.persistence.AttemptRepository
import de.optadata.odil.learnwithme.assessment.internal.persistence.SessionRepository
import de.optadata.odil.learnwithme.assessment.internal.selection.ItemSelectionService
import de.optadata.odil.learnwithme.assessment.internal.selection.SelectedItem
import de.optadata.odil.learnwithme.shared.ConflictException
import de.optadata.odil.learnwithme.shared.JsonMapper
import de.optadata.odil.learnwithme.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class SessionSummary(val attemptCount: Int, val accuracy: Float, val durationMs: Long)

/** D1: Session starten (erste Frage < 500 ms — kein LLM im Pfad), D8-Abschluss/-Zusammenfassung. */
@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    private val attemptRepository: AttemptRepository,
    private val selectionService: ItemSelectionService,
) {
    private val mapper = JsonMapper.instance

    @Transactional
    fun start(
        workspaceId: UUID,
        userId: UUID,
        scopeKind: SessionScopeKind,
        scopeId: UUID?,
        goalKind: SessionGoalKind,
        goalValue: Int,
    ): Pair<Session, SelectedItem?> {
        val session = sessionRepository.save(
            Session(workspaceId = workspaceId, userId = userId, scopeKind = scopeKind, scopeId = scopeId, goalKind = goalKind, goalValue = goalValue),
        )
        return session to selectionService.selectNext(session)
    }

    fun get(workspaceId: UUID, sessionId: UUID): Session =
        sessionRepository.findByIdAndWorkspaceId(sessionId, workspaceId) ?: throw NotFoundException("Session $sessionId nicht gefunden")

    fun peekNext(workspaceId: UUID, sessionId: UUID): SelectedItem? = selectionService.selectNext(get(workspaceId, sessionId))

    @Transactional
    fun finish(workspaceId: UUID, sessionId: UUID): Session {
        val session = get(workspaceId, sessionId)
        if (session.endedAt != null) throw ConflictException("Session $sessionId ist bereits beendet")

        val attempts = attemptRepository.findAllBySessionId(sessionId)
        val graded = attempts.filter { it.outcome != AttemptOutcome.SKIPPED }
        val accuracy = if (graded.isEmpty()) 0f else graded.map { it.score }.average().toFloat()
        val summary = SessionSummary(
            attemptCount = attempts.size,
            accuracy = accuracy,
            durationMs = Duration.between(session.startedAt, Instant.now()).toMillis(),
        )

        session.endedAt = Instant.now()
        session.summary = mapper.writeValueAsString(summary)
        return sessionRepository.save(session)
    }
}
