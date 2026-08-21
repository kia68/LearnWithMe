package de.optadata.odil.learnwithme.assessment.internal.grading

import de.optadata.odil.learnwithme.ai.LlmGateway
import de.optadata.odil.learnwithme.ai.LlmResult
import de.optadata.odil.learnwithme.ai.StructuredRequest
import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test

class FreeTextGraderTest {

    private val llmGateway = mockk<LlmGateway>()
    private val grader = FreeTextGrader(llmGateway)
    private val workspaceId = UUID.randomUUID()
    private val rubric = listOf(RubricCriterionView("Nennt ACID korrekt", 2), RubricCriterionView("Erklärt Konsistenz", 1))

    private fun stubLlmResponse(draft: FreeTextGradeDraft) {
        every { llmGateway.complete(any<StructuredRequest<FreeTextGradeDraft>>()) } returns
            LlmResult(draft, "gpt-4o-mini", 100, 50, 10L, 200L)
    }

    @Test
    fun `full marks on every criterion scores 1 and is CORRECT`() {
        stubLlmResponse(FreeTextGradeDraft(awardedPoints = listOf(2, 1), feedback = "Sehr gut."))

        val result = grader.grade(workspaceId, "Was ist ACID?", rubric, "Musterantwort", "Antwort des Lernenden")

        result.score shouldBe 1f
        result.outcome shouldBe AttemptOutcome.CORRECT
        result.criterionScores shouldBe listOf(
            CriterionScoreView("Nennt ACID korrekt", 2, 2),
            CriterionScoreView("Erklärt Konsistenz", 1, 1),
        )
    }

    @Test
    fun `zero marks on every criterion scores 0 and is INCORRECT`() {
        stubLlmResponse(FreeTextGradeDraft(awardedPoints = listOf(0, 0), feedback = "Leider falsch."))

        val result = grader.grade(workspaceId, "Was ist ACID?", rubric, "Musterantwort", "Falsche Antwort")

        result.score shouldBe 0f
        result.outcome shouldBe AttemptOutcome.INCORRECT
    }

    @Test
    fun `partial marks give a fractional score and PARTIAL outcome`() {
        stubLlmResponse(FreeTextGradeDraft(awardedPoints = listOf(1, 0), feedback = "Teilweise richtig."))

        val result = grader.grade(workspaceId, "Was ist ACID?", rubric, "Musterantwort", "Teilweise richtige Antwort")

        result.score shouldBe (1f / 3f)
        result.outcome shouldBe AttemptOutcome.PARTIAL
    }

    @Test
    fun `awarded points beyond a criterion's max are clamped`() {
        stubLlmResponse(FreeTextGradeDraft(awardedPoints = listOf(99, -5), feedback = "..."))

        val result = grader.grade(workspaceId, "Was ist ACID?", rubric, "Musterantwort", "Antwort")

        result.criterionScores shouldBe listOf(
            CriterionScoreView("Nennt ACID korrekt", 2, 2),
            CriterionScoreView("Erklärt Konsistenz", 0, 1),
        )
    }

    @Test
    fun `a shorter awardedPoints list than the rubric treats missing entries as zero`() {
        stubLlmResponse(FreeTextGradeDraft(awardedPoints = listOf(2), feedback = "Nur ein Kriterium beantwortet."))

        val result = grader.grade(workspaceId, "Was ist ACID?", rubric, "Musterantwort", "Antwort")

        result.criterionScores shouldBe listOf(
            CriterionScoreView("Nennt ACID korrekt", 2, 2),
            CriterionScoreView("Erklärt Konsistenz", 0, 1),
        )
        result.score shouldBe (2f / 3f)
    }
}
