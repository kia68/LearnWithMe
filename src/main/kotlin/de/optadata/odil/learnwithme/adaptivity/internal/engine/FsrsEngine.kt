package de.optadata.odil.learnwithme.adaptivity.internal.engine

import de.optadata.odil.learnwithme.adaptivity.internal.domain.LearnerState
import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** FSRS-Grade (§11.4-Tabelle): 1=AGAIN, 2=HARD, 3=GOOD, 4=EASY. */
enum class FsrsGrade(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

data class FsrsUpdateResult(
    val stability: Float,
    val difficulty: Float,
    val state: LearnerState,
    val reps: Int,
    val lapses: Int,
    val dueAt: Instant,
)

/**
 * FSRS-Scheduler (ADR-006, §11.4): Difficulty/Stability/Retrievability, getrennt vom
 * Elo-Fähigkeitsmodell ([EloEngine]). Standardgewichte des offenen FSRS-4.5-Algorithmus
 * (Formeln nach der öffentlichen FSRS-Spezifikation nachimplementiert; genaue Werte laut
 * docs/PLAN.md §20 O-3 vor Produktivbetrieb gegen die Referenz-Testvektoren des
 * Open-Source-Projekts verifizieren — hier ungetestet gegen diese Referenz, siehe docs/progress.md).
 * Modelliert bewusst keine Kurzzeit-/Gleichtags-Reviews (die offiziellen `w[17]`/`w[18]` entfallen).
 */
object FsrsEngine {

    private const val DECAY = -0.5
    private val FACTOR = 19.0 / 81.0

    private val W = doubleArrayOf(
        0.4072, 1.1829, 3.1262, 15.4722, // w0..3: initiale Stability je Grade (AGAIN..EASY)
        7.2102, // w4: initiale Difficulty (Basis)
        0.5316, // w5: initiale Difficulty (Grade-Neigung)
        1.0651, // w6: Difficulty-Delta pro Review
        0.0234, // w7: Mean-Reversion-Gewicht Richtung initialer EASY-Difficulty
        1.6160, // w8
        0.1544, // w9
        1.0824, // w10
        1.9813, // w11: Stability nach AGAIN
        0.0953, // w12
        0.2975, // w13
        2.2042, // w14
        0.2407, // w15: HARD-Malus
        2.9466, // w16: EASY-Bonus
    )

    /** `R(t) = (1 + FACTOR · t/S)^DECAY` — Abrufwahrscheinlichkeit nach `t` Tagen. */
    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1.0 + FACTOR * elapsedDays / stability).pow(DECAY)

    /** `I(R_target) = (S/FACTOR) · (R_target^(1/DECAY) - 1)` — nächstes Intervall in Tagen. */
    fun intervalDays(targetRetention: Double, stability: Double): Double =
        max(1.0, (stability / FACTOR) * (targetRetention.pow(1.0 / DECAY) - 1.0))

    /** §11.4-Tabelle: Score → FSRS-Grade. „>0.95 und schnell" vereinfacht zu reinem Score-Schwellwert
     * — Antwortzeit-Median pro Item wird (noch) nicht getrackt, siehe docs/progress.md. */
    fun gradeFromScore(score: Float): FsrsGrade = when {
        score < 0.4f -> FsrsGrade.AGAIN
        score < 0.7f -> FsrsGrade.HARD
        score <= 0.95f -> FsrsGrade.GOOD
        else -> FsrsGrade.EASY
    }

    fun review(
        stability: Float?,
        difficulty: Float?,
        state: LearnerState,
        reps: Int,
        lapses: Int,
        lastReviewAt: Instant?,
        grade: FsrsGrade,
        targetRetention: Double,
        now: Instant,
    ): FsrsUpdateResult {
        val initialEasyDifficulty = clampDifficulty(W[4] - (FsrsGrade.EASY.value - 3) * W[5])

        val (newStability, newDifficulty, newState, newReps, newLapses) =
            if (state == LearnerState.NEW || stability == null || difficulty == null) {
                val s0 = W[grade.value - 1]
                val d0 = clampDifficulty(W[4] - (grade.value - 3) * W[5])
                val nextState = if (grade == FsrsGrade.AGAIN) LearnerState.RELEARNING else LearnerState.REVIEW
                Quintuple(s0, d0, nextState, if (grade == FsrsGrade.AGAIN) 0 else 1, if (grade == FsrsGrade.AGAIN) 1 else 0)
            } else {
                val elapsedDays = elapsedDaysSince(lastReviewAt, now)
                val r = retrievability(elapsedDays, stability.toDouble())
                val d = difficulty.toDouble()
                val revertedDifficulty = W[7] * initialEasyDifficulty + (1 - W[7]) * (d - W[6] * (grade.value - 3))
                val nd = clampDifficulty(revertedDifficulty)

                if (grade == FsrsGrade.AGAIN) {
                    val ns = W[11] * d.pow(-W[12]) * (((stability.toDouble() + 1).pow(W[13])) - 1) * exp((1 - r) * W[14])
                    Quintuple(ns, nd, LearnerState.RELEARNING, reps, lapses + 1)
                } else {
                    val hardPenalty = if (grade == FsrsGrade.HARD) W[15] else 1.0
                    val easyBonus = if (grade == FsrsGrade.EASY) W[16] else 1.0
                    val ns = stability.toDouble() * (
                        1 + exp(W[8]) * (11 - d) * stability.toDouble().pow(-W[9]) *
                            (exp((1 - r) * W[10]) - 1) * hardPenalty * easyBonus
                        )
                    Quintuple(ns, nd, LearnerState.REVIEW, reps + 1, lapses)
                }
            }

        val dueAt = now.plus(Duration.ofDays(intervalDays(targetRetention, newStability).roundToLong()))
        return FsrsUpdateResult(newStability.toFloat(), newDifficulty.toFloat(), newState, newReps, newLapses, dueAt)
    }

    private fun elapsedDaysSince(lastReviewAt: Instant?, now: Instant): Double {
        if (lastReviewAt == null) return 0.0
        return max(0.0, Duration.between(lastReviewAt, now).toMillis() / 86_400_000.0)
    }

    private fun clampDifficulty(value: Double): Double = max(1.0, min(10.0, value))

    private data class Quintuple(val stability: Double, val difficulty: Double, val state: LearnerState, val reps: Int, val lapses: Int)
}
