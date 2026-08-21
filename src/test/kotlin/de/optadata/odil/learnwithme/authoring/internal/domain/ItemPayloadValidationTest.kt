package de.optadata.odil.learnwithme.authoring.internal.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ItemPayloadValidationTest {

    private fun option(id: String, text: String, correct: Boolean) = Option(id, text, correct, "Begründung für $id.")

    @Test
    fun `valid MC_SINGLE has no issues`() {
        val payload = McSinglePayload(
            listOf(option("a", "3NF", true), option("b", "2NF", false), option("c", "1NF", false)),
        )
        payload.validate(2.0).size shouldBe 0
    }

    @Test
    fun `MC_SINGLE with two correct options is rejected`() {
        val payload = McSinglePayload(listOf(option("a", "3NF", true), option("b", "2NF", true), option("c", "1NF", false)))
        payload.validate(2.0).any { it.code == "CORRECT_COUNT" } shouldBe true
    }

    @Test
    fun `MC_SINGLE with forbidden option pattern is rejected`() {
        val payload = McSinglePayload(
            listOf(option("a", "3NF", true), option("b", "2NF", false), option("c", "Alle der genannten", false)),
        )
        payload.validate(2.0).any { it.code == "FORBIDDEN_PATTERN" } shouldBe true
    }

    @Test
    fun `MC_SINGLE with a giveaway length cue is rejected`() {
        val payload = McSinglePayload(
            listOf(
                option("a", "X", true),
                option("b", "Dies ist eine wesentlich, wesentlich, wesentlich längere Option als die anderen beiden hier", false),
                option("c", "Y", false),
            ),
        )
        payload.validate(2.0).any { it.code == "LENGTH_CUE" } shouldBe true
    }

    @Test
    fun `MC_SINGLE without rationale is rejected`() {
        val payload = McSinglePayload(
            listOf(Option("a", "3NF", true, ""), option("b", "2NF", false), option("c", "1NF", false)),
        )
        payload.validate(2.0).any { it.code == "MISSING_RATIONALE" } shouldBe true
    }

    @Test
    fun `MC_MULTI requires at least two correct options`() {
        val payload = McMultiPayload(listOf(option("a", "3NF", true), option("b", "2NF", false), option("c", "1NF", false)))
        payload.validate(2.0).any { it.code == "CORRECT_COUNT" } shouldBe true
    }

    @Test
    fun `ORDERING with a correctOrder that is not a permutation is rejected`() {
        val payload = OrderingPayload(
            elements = listOf(OrderingElement("1", "a"), OrderingElement("2", "b"), OrderingElement("3", "c")),
            correctOrder = listOf("1", "2"),
        )
        payload.validate(2.0).any { it.code == "ORDER_MISMATCH" } shouldBe true
    }

    @Test
    fun `valid ORDERING has no issues`() {
        val payload = OrderingPayload(
            elements = listOf(OrderingElement("1", "a"), OrderingElement("2", "b"), OrderingElement("3", "c")),
            correctOrder = listOf("2", "1", "3"),
        )
        payload.validate(2.0).size shouldBe 0
    }

    @Test
    fun `CLOZE with mismatched placeholders is rejected`() {
        val payload = ClozePayload(
            template = "Der {{1}} ist wichtig.",
            blanks = listOf(ClozeBlank(listOf("Wert")), ClozeBlank(listOf("Preis"))),
        )
        payload.validate(2.0).any { it.code == "PLACEHOLDER_MISMATCH" } shouldBe true
    }

    @Test
    fun `valid CLOZE has no issues`() {
        val payload = ClozePayload(template = "Der {{1}} kostet {{2}}.", blanks = listOf(ClozeBlank(listOf("Wert")), ClozeBlank(listOf("Preis"))))
        payload.validate(2.0).size shouldBe 0
    }

    @Test
    fun `MATCHING with unknown pair reference is rejected`() {
        val payload = MatchingPayload(
            left = listOf(MatchingItem("l1", "A"), MatchingItem("l2", "B"), MatchingItem("l3", "C")),
            right = listOf(MatchingItem("r1", "X"), MatchingItem("r2", "Y"), MatchingItem("r3", "Z")),
            pairs = listOf(MatchingPair("l1", "r1"), MatchingPair("l2", "r-unknown"), MatchingPair("l3", "r3")),
        )
        payload.validate(2.0).any { it.code == "UNKNOWN_PAIR_REFERENCE" } shouldBe true
    }

    @Test
    fun `valid SHORT_ANSWER has no issues`() {
        val payload = ShortAnswerPayload(
            rubric = listOf(RubricCriterion("Nennt ACID korrekt", 2), RubricCriterion("Erklärt Konsistenz", 1)),
            referenceAnswer = "ACID steht für Atomicity, Consistency, Isolation, Durability.",
        )
        payload.validate(2.0).size shouldBe 0
    }

    @Test
    fun `SHORT_ANSWER with an empty rubric is rejected`() {
        val payload = ShortAnswerPayload(rubric = emptyList(), referenceAnswer = "Musterantwort")
        payload.validate(2.0).any { it.code == "EMPTY_RUBRIC" } shouldBe true
    }

    @Test
    fun `SHORT_ANSWER with a non-positive criterion score is rejected`() {
        val payload = ShortAnswerPayload(rubric = listOf(RubricCriterion("Kriterium", 0)), referenceAnswer = "Musterantwort")
        payload.validate(2.0).any { it.code == "INVALID_POINTS" } shouldBe true
    }

    @Test
    fun `SHORT_ANSWER with a blank reference answer is rejected`() {
        val payload = ShortAnswerPayload(rubric = listOf(RubricCriterion("Kriterium", 1)), referenceAnswer = "  ")
        payload.validate(2.0).any { it.code == "EMPTY_REFERENCE_ANSWER" } shouldBe true
    }
}
