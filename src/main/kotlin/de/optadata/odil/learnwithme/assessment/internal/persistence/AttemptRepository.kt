package de.optadata.odil.learnwithme.assessment.internal.persistence

import de.optadata.odil.learnwithme.assessment.internal.domain.Attempt
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AttemptRepository : JpaRepository<Attempt, Long> {

    /** §11.3 Schritt 2: „nicht in den letzten [pageable.pageSize] Attempts gesehen" + Typ-Rotation
     * (D9) — beides braucht die jüngsten Attempts der laufenden Session. */
    fun findAllBySessionIdOrderByCreatedAtDesc(sessionId: UUID, pageable: Pageable): List<Attempt>

    fun countBySessionId(sessionId: UUID): Long

    /** D8-Zusammenfassung (`POST /sessions/{id}/finish`). */
    fun findAllBySessionId(sessionId: UUID): List<Attempt>

    /** Epic H: Polling-Ziel für den asynchronen `SHORT_ANSWER`-Grade — `null`, solange
     * `GradeFreeTextJobHandler` den Attempt noch nicht persistiert hat. */
    fun findFirstBySessionIdAndItemIdOrderByCreatedAtDesc(sessionId: UUID, itemId: UUID): Attempt?
}
