package de.optadata.odil.learnwithme.adaptivity.internal.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** θ/d bleiben im Logit-Band [-4, +4] (Domänen-Invariante, §5.2). */
private const val THETA_MIN = -4.0f
private const val THETA_MAX = 4.0f

data class EloUpdateResult(
    val thetaAfter: Float,
    val thetaN: Int,
    val itemDifficultyAfter: Float,
    val itemDifficultyN: Int,
    val pExpected: Float,
)

/** Elo-Fähigkeitsmodell (ADR-005, §11.2). Reine Mathematik, kein I/O — bewusst zustandslos:
 * der Aufrufer (`adaptivity.internal.service`) liest/schreibt den persistierten Zustand. */
object EloEngine {

    /** `P(korrekt) = 1 / (1 + exp(-(θ_u - d_i)))`. */
    fun successProbability(theta: Float, itemDifficulty: Float): Float =
        (1.0 / (1.0 + exp(-(theta - itemDifficulty).toDouble()))).toFloat()

    /** Unsicherheitsabhängiger K-Faktor: `K(n) = a / (1 + b·n)` — je mehr Beobachtungen, desto
     * stabiler. Neue Items/Nutzer bewegen sich schnell (Cold-Start-Vorteil, ADR-005 [3][4]). */
    fun kFactor(a: Float, b: Float, n: Int): Float = a / (1 + b * n)

    /** [score] ist das kontinuierliche Grading-Ergebnis `r ∈ [0,1]` (§10.3) — Elo braucht dafür
     * keine Anpassung. */
    fun update(
        thetaBefore: Float,
        thetaN: Int,
        itemDifficultyBefore: Float,
        itemDifficultyN: Int,
        score: Float,
        userKA: Float,
        userKB: Float,
        itemKA: Float,
        itemKB: Float,
    ): EloUpdateResult {
        val p = successProbability(thetaBefore, itemDifficultyBefore)
        val kUser = kFactor(userKA, userKB, thetaN)
        val kItem = kFactor(itemKA, itemKB, itemDifficultyN)
        val thetaAfter = clampTheta(thetaBefore + kUser * (score - p))
        val itemDifficultyAfter = clampTheta(itemDifficultyBefore + kItem * (p - score))
        return EloUpdateResult(thetaAfter, thetaN + 1, itemDifficultyAfter, itemDifficultyN + 1, p)
    }

    private fun clampTheta(value: Float): Float = max(THETA_MIN, min(THETA_MAX, value))
}
