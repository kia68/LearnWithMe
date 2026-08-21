package de.optadata.odil.learnwithme.adaptivity.internal.engine

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.test.Test

class EloEngineTest {

    @Test
    fun `equal theta and difficulty give 50 percent success probability`() {
        EloEngine.successProbability(0f, 0f) shouldBe 0.5f
    }

    @Test
    fun `higher theta than difficulty gives above 50 percent success probability`() {
        EloEngine.successProbability(2f, 0f) shouldBeGreaterThan 0.5f
    }

    @Test
    fun `correct answer raises theta and lowers the item's calibrated difficulty`() {
        val result = EloEngine.update(
            thetaBefore = 0f, thetaN = 0, itemDifficultyBefore = 0f, itemDifficultyN = 0,
            score = 1f, userKA = 0.6f, userKB = 0.05f, itemKA = 0.8f, itemKB = 0.10f,
        )
        result.thetaAfter shouldBeGreaterThan 0f
        result.itemDifficultyAfter shouldBeLessThan 0f // d_i ← d_i + K_i·(P−r): ein Erfolg (r=1 > P) macht das Item "leichter" kalibriert
        result.thetaN shouldBe 1
        result.itemDifficultyN shouldBe 1
    }

    @Test
    fun `incorrect answer lowers theta`() {
        val result = EloEngine.update(
            thetaBefore = 0f, thetaN = 0, itemDifficultyBefore = 0f, itemDifficultyN = 0,
            score = 0f, userKA = 0.6f, userKB = 0.05f, itemKA = 0.8f, itemKB = 0.10f,
        )
        result.thetaAfter shouldBeLessThan 0f
    }

    @Test
    fun `k factor shrinks as observation count grows`() {
        val kEarly = EloEngine.kFactor(0.6f, 0.05f, 0)
        val kLate = EloEngine.kFactor(0.6f, 0.05f, 100)
        kLate shouldBeLessThan kEarly
    }

    @Test
    fun `theta stays within the logit band even after many correct answers`() {
        var theta = 0f
        var n = 0
        repeat(200) {
            val result = EloEngine.update(theta, n, itemDifficultyBefore = -4f, itemDifficultyN = 50, score = 1f, userKA = 0.6f, userKB = 0.05f, itemKA = 0.8f, itemKB = 0.10f)
            theta = result.thetaAfter
            n = result.thetaN
        }
        abs(theta) shouldBeLessThan 4.01f
    }
}
