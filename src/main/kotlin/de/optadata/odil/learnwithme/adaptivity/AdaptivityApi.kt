package de.optadata.odil.learnwithme.adaptivity

import java.time.Instant
import java.util.UUID

/** Fähigkeits-/Gedächtnisstand eines Nutzers zu einem Konzept (§11.1, D7). */
data class ConceptProgressView(
    val conceptId: UUID,
    val theta: Float,
    val mastery: Float,
    val state: String,
    val reps: Int,
    val lapses: Int,
    val dueAt: Instant?,
)

/** Ergebnis eines Elo+FSRS-Updates nach einer Antwort (D3/D5, §11.2/§11.4). */
data class RecordAttemptResult(
    val thetaBefore: Float,
    val thetaAfter: Float,
    val pExpected: Float,
    val mastery: Float,
    val newItemDifficulty: Float,
    val newItemDifficultyN: Int,
    val dueAt: Instant,
)

/**
 * Öffentlicher Port des `adaptivity`-Moduls (ADR-005/ADR-006). Bewusst frei von jeder
 * `authoring`-Abhängigkeit (§6.3: „adaptivity kennt kein LLM" — und auch keine Items): Item-Schwierigkeit
 * wird als Wert hinein- und wieder herausgereicht, `assessment` schreibt sie über [de.optadata.odil.learnwithme.authoring.AuthoringApi]
 * zurück. So bleibt die Adaptionslogik reine, isoliert testbare Mathematik (P4).
 */
interface AdaptivityApi {
    /** Reine Erfolgswahrscheinlichkeit nach dem Rasch-äquivalenten Logit-Modell (§11.2) — auch
     * für die Item-Auswahl (§11.3) und `next.meta.expectedSuccess` (§9.3) gebraucht. */
    fun expectedSuccess(theta: Float, itemDifficulty: Float): Float

    /** Aktueller Stand, oder ein unpersistierter Default (θ=0, mastery=0.5, NEW), falls der
     * Nutzer das Konzept noch nie beantwortet hat (§11.2: „Neuer Nutzer: θ=0"). */
    fun getProgress(workspaceId: UUID, userId: UUID, conceptId: UUID): ConceptProgressView

    fun listProgress(workspaceId: UUID, userId: UUID, conceptIds: List<UUID>): List<ConceptProgressView>

    /** Alle persistierten Zustände eines Nutzers — Basis für `GET /progress/overview` (D7). Nur
     * bereits beantwortete Konzepte (kein Default-Eintrag für unberührte Konzepte, anders als
     * [getProgress]/[listProgress]). */
    fun listAllProgress(workspaceId: UUID, userId: UUID): List<ConceptProgressView>

    /** Fällige Konzepte (FSRS `due_at <= now`) — Basis für Scope `DUE_REVIEW` (D1) und
     * `GET /progress/due` (D5/D7). */
    fun listDue(workspaceId: UUID, userId: UUID, now: Instant): List<ConceptProgressView>

    /** Elo- (D3) und FSRS-Update (D5) in einem Schritt, atomar gegen denselben Zustand.
     * [score] ist das kontinuierliche Grading-Ergebnis `r ∈ [0,1]` (§10.3). */
    fun recordAttempt(
        workspaceId: UUID,
        userId: UUID,
        conceptId: UUID,
        itemDifficulty: Float,
        itemDifficultyN: Int,
        score: Float,
        now: Instant,
    ): RecordAttemptResult
}
