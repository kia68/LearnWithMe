package de.optadata.odil.learnwithme.content.internal.chunking

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TokenEstimatorTest {

    @Test
    fun `empty text has zero tokens`() {
        TokenEstimator.estimate("") shouldBe 0
    }

    @Test
    fun `non-empty text has at least one token`() {
        TokenEstimator.estimate("a") shouldBe 1
    }

    @Test
    fun `roughly four characters per token`() {
        TokenEstimator.estimate("a".repeat(400)) shouldBe 100
    }
}
