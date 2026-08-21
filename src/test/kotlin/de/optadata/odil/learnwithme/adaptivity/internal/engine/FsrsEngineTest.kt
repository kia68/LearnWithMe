package de.optadata.odil.learnwithme.adaptivity.internal.engine

import de.optadata.odil.learnwithme.adaptivity.internal.domain.LearnerState
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.time.Instant
import kotlin.test.Test

class FsrsEngineTest {

    @Test
    fun `score below 0_4 maps to AGAIN`() {
        FsrsEngine.gradeFromScore(0.1f) shouldBe FsrsGrade.AGAIN
    }

    @Test
    fun `score between 0_4 and 0_7 maps to HARD`() {
        FsrsEngine.gradeFromScore(0.5f) shouldBe FsrsGrade.HARD
    }

    @Test
    fun `score between 0_7 and 0_95 maps to GOOD`() {
        FsrsEngine.gradeFromScore(0.8f) shouldBe FsrsGrade.GOOD
    }

    @Test
    fun `score above 0_95 maps to EASY`() {
        FsrsEngine.gradeFromScore(0.99f) shouldBe FsrsGrade.EASY
    }

    @Test
    fun `first review on a NEW card initializes stability and difficulty and schedules a future due date`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val result = FsrsEngine.review(
            stability = null, difficulty = null, state = LearnerState.NEW, reps = 0, lapses = 0,
            lastReviewAt = null, grade = FsrsGrade.GOOD, targetRetention = 0.9, now = now,
        )
        result.stability shouldBeGreaterThan 0f
        result.state shouldBe LearnerState.REVIEW
        result.reps shouldBe 1
        (result.dueAt.isAfter(now)) shouldBe true
    }

    @Test
    fun `AGAIN on a NEW card starts relearning with a lapse`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val result = FsrsEngine.review(
            stability = null, difficulty = null, state = LearnerState.NEW, reps = 0, lapses = 0,
            lastReviewAt = null, grade = FsrsGrade.AGAIN, targetRetention = 0.9, now = now,
        )
        result.state shouldBe LearnerState.RELEARNING
        result.lapses shouldBe 1
        result.reps shouldBe 0
    }

    @Test
    fun `a successful review grows stability further than the initial value`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val first = FsrsEngine.review(null, null, LearnerState.NEW, 0, 0, null, FsrsGrade.GOOD, 0.9, now)
        val second = FsrsEngine.review(
            stability = first.stability, difficulty = first.difficulty, state = first.state,
            reps = first.reps, lapses = first.lapses, lastReviewAt = now,
            grade = FsrsGrade.GOOD, targetRetention = 0.9, now = now.plusSeconds(first.stability.toLong() * 86_400),
        )
        second.stability shouldBeGreaterThan first.stability
        second.reps shouldBe 2
    }

    @Test
    fun `AGAIN after a review moves the card back to RELEARNING and increments lapses`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val first = FsrsEngine.review(null, null, LearnerState.NEW, 0, 0, null, FsrsGrade.GOOD, 0.9, now)
        val relapse = FsrsEngine.review(
            stability = first.stability, difficulty = first.difficulty, state = first.state,
            reps = first.reps, lapses = first.lapses, lastReviewAt = now,
            grade = FsrsGrade.AGAIN, targetRetention = 0.9, now = now.plusSeconds(1),
        )
        relapse.state shouldBe LearnerState.RELEARNING
        relapse.lapses shouldBe 1
    }
}
