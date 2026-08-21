package de.optadata.odil.learnwithme.authoring

import java.util.UUID

/** Kandidat für die Item-Auswahl (§11.3) — nur die für die Selection-Policy nötigen Felder.
 * [parentItemId] trägt die Paraphrase-Herkunft (E6) — die Selection-Policy bevorzugt damit die
 * Paraphrase eines zuletzt falsch beantworteten Items. */
data class CandidateItemView(
    val id: UUID,
    val conceptId: UUID,
    val type: String,
    val difficulty: Float,
    val difficultyN: Int,
    val parentItemId: UUID?,
)

/** Vollständige Sicht auf ein veröffentlichtes Item — Basis für Session-Delivery (D1/D2) und
 * Feedback mit Beleg (D4). `payloadJson` bleibt roher Jackson-JSON-String: `assessment` braucht
 * für die Bewertung (§10.2/10.3) den typspezifischen Payload, aber `authoring.internal.domain.ItemPayload`
 * ist `internal` — die Deserialisierung passiert daher modul-lokal in `assessment` über denselben
 * `type`-Diskriminator, den `authoring` hier mitliefert (kein Payload-Typ wandert über die Modulgrenze). */
data class PublishedItemView(
    val id: UUID,
    val conceptId: UUID,
    val type: String,
    val stem: String,
    val payloadJson: String,
    val explanation: String,
    val sourceChunkId: UUID,
    val difficulty: Float,
    val difficultyN: Int,
)

/** Öffentlicher Port des `authoring`-Moduls — für `assessment` (Epic D: Item-Delivery,
 * Elo-Kalibrierung auf der Item-Seite, D6-Qualitätssignal). Bewusst erst jetzt nachgezogen —
 * in Epic C gab es noch keinen Konsumenten außerhalb des Moduls (siehe docs/progress.md). */
interface AuthoringApi {
    /** Veröffentlichte Items eines Konzepts — Kandidatenpool für die Item-Auswahl (§11.3). */
    fun listPublishedForConcept(workspaceId: UUID, conceptId: UUID): List<CandidateItemView>

    /** Wirft `NotFoundException`, wenn das Item nicht existiert, nicht zu [workspaceId] gehört
     * oder nicht `PUBLISHED` ist. */
    fun getPublished(workspaceId: UUID, itemId: UUID): PublishedItemView

    /** Elo-Update der Item-Schwierigkeit nach einer Antwort (D3, §11.2). */
    fun updateCalibration(itemId: UUID, difficulty: Float, difficultyN: Int, pCorrect: Float?)

    /** D6: Skip fließt als Qualitätssignal in die Item-Bank, ohne Fehler zu zählen. */
    fun recordSkip(itemId: UUID)
}
