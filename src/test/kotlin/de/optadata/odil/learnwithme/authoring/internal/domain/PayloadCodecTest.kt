package de.optadata.odil.learnwithme.authoring.internal.domain

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PayloadCodecTest {

    @Test
    fun `round-trips McSinglePayload through the Kotlin data class constructor`() {
        val payload = McSinglePayload(
            options = listOf(
                Option("a", "3NF", correct = true, rationale = "Korrekt, keine transitive Abhängigkeit."),
                Option("b", "2NF", correct = false, rationale = "2NF erlaubt noch transitive Abhängigkeiten."),
                Option("c", "1NF", correct = false, rationale = "1NF ist noch schwächer."),
            ),
        )

        val json = PayloadCodec.serialize(payload)
        val decoded = PayloadCodec.deserialize(ItemType.MC_SINGLE, json)

        decoded shouldBe payload
    }

    @Test
    fun `round-trips OrderingPayload`() {
        val payload = OrderingPayload(
            elements = listOf(OrderingElement("1", "Erst"), OrderingElement("2", "Dann"), OrderingElement("3", "Zuletzt")),
            correctOrder = listOf("1", "2", "3"),
        )

        val decoded = PayloadCodec.deserialize(ItemType.ORDERING, PayloadCodec.serialize(payload))

        decoded shouldBe payload
    }

    @Test
    fun `round-trips ShortAnswerPayload`() {
        val payload = ShortAnswerPayload(
            rubric = listOf(RubricCriterion("Nennt ACID korrekt", 2), RubricCriterion("Erklärt Konsistenz", 1)),
            referenceAnswer = "ACID steht für Atomicity, Consistency, Isolation, Durability.",
        )

        val decoded = PayloadCodec.deserialize(ItemType.SHORT_ANSWER, PayloadCodec.serialize(payload))

        decoded shouldBe payload
    }

    @Test
    fun `round-trips NumericPayload`() {
        val payload = NumericPayload(value = 9.81, tolerance = 0.05, unit = "m/s^2")

        val decoded = PayloadCodec.deserialize(ItemType.NUMERIC, PayloadCodec.serialize(payload))

        decoded shouldBe payload
    }

    @Test
    fun `round-trips CategorizationPayload`() {
        val payload = CategorizationPayload(
            buckets = listOf(CategorizationBucket("b1", "Fisch"), CategorizationBucket("b2", "Säugetier")),
            elements = listOf(CategorizationElement("e1", "Hai", "b1"), CategorizationElement("e2", "Wal", "b2")),
        )

        val decoded = PayloadCodec.deserialize(ItemType.CATEGORIZATION, PayloadCodec.serialize(payload))

        decoded shouldBe payload
    }

    @Test
    fun `round-trips CodeOutputPayload`() {
        val payload = CodeOutputPayload(snippet = "print(1 + 1)", language = "python", expected = "2")

        val decoded = PayloadCodec.deserialize(ItemType.CODE_OUTPUT, PayloadCodec.serialize(payload))

        decoded shouldBe payload
    }
}
