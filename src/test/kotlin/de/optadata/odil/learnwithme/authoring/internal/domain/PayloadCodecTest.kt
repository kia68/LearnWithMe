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
}
