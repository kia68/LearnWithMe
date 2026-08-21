package de.optadata.odil.learnwithme.analytics.internal.classification

import de.optadata.odil.learnwithme.analytics.internal.config.AnalyticsProperties
import de.optadata.odil.learnwithme.analytics.internal.domain.DetectionMethod
import de.optadata.odil.learnwithme.analytics.internal.domain.ErrorCategory
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ErrorClassifierTest {

    private val properties = AnalyticsProperties()

    @Test
    fun `a strong learner failing a much easier item is AMBIGUOUS_ITEM`() {
        val result = ErrorClassifier.classify(
            itemType = "MC_SINGLE", expectedSuccess = 0.5f, elapsedMs = 5000,
            thetaBefore = 2.0f, itemDifficulty = 0.0f, chosenOptionMisconceptionCategory = "FACTUAL_GAP",
            properties = properties,
        )
        result.category shouldBe ErrorCategory.AMBIGUOUS_ITEM
        result.detectedBy shouldBe DetectionMethod.HEURISTIC
    }

    @Test
    fun `a fast wrong answer to an easy item is CARELESS`() {
        val result = ErrorClassifier.classify(
            itemType = "MC_SINGLE", expectedSuccess = 0.9f, elapsedMs = 1500,
            thetaBefore = 0.5f, itemDifficulty = 0.4f, chosenOptionMisconceptionCategory = null,
            properties = properties,
        )
        result.category shouldBe ErrorCategory.CARELESS
    }

    @Test
    fun `a tagged distractor is classified by its own misconceptionCategory`() {
        val result = ErrorClassifier.classify(
            itemType = "MC_SINGLE", expectedSuccess = 0.6f, elapsedMs = 8000,
            thetaBefore = 0f, itemDifficulty = 0f, chosenOptionMisconceptionCategory = "TERM_CONFUSION",
            properties = properties,
        )
        result.category shouldBe ErrorCategory.TERM_CONFUSION
        result.detectedBy shouldBe DetectionMethod.RATIONALE
    }

    @Test
    fun `ORDERING without a tag falls back to PROCEDURAL`() {
        val result = ErrorClassifier.classify(
            itemType = "ORDERING", expectedSuccess = 0.6f, elapsedMs = 8000,
            thetaBefore = 0f, itemDifficulty = 0f, chosenOptionMisconceptionCategory = null,
            properties = properties,
        )
        result.category shouldBe ErrorCategory.PROCEDURAL
    }

    @Test
    fun `MC_SINGLE without a tag falls back to FACTUAL_GAP`() {
        val result = ErrorClassifier.classify(
            itemType = "MC_SINGLE", expectedSuccess = 0.6f, elapsedMs = 8000,
            thetaBefore = 0f, itemDifficulty = 0f, chosenOptionMisconceptionCategory = null,
            properties = properties,
        )
        result.category shouldBe ErrorCategory.FACTUAL_GAP
    }

    @Test
    fun `an unparseable tag is ignored and falls back like a missing tag`() {
        val result = ErrorClassifier.classify(
            itemType = "MC_SINGLE", expectedSuccess = 0.6f, elapsedMs = 8000,
            thetaBefore = 0f, itemDifficulty = 0f, chosenOptionMisconceptionCategory = "NOT_A_REAL_CATEGORY",
            properties = properties,
        )
        result.category shouldBe ErrorCategory.FACTUAL_GAP
    }

    @Test
    fun `a tag of CARELESS or AMBIGUOUS_ITEM from generation time is not trusted as a distractor tag`() {
        val result = ErrorClassifier.classify(
            itemType = "MC_SINGLE", expectedSuccess = 0.6f, elapsedMs = 8000,
            thetaBefore = 0f, itemDifficulty = 0f, chosenOptionMisconceptionCategory = "CARELESS",
            properties = properties,
        )
        result.category shouldBe ErrorCategory.FACTUAL_GAP
    }
}
