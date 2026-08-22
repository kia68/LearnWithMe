package de.optadata.odil.learnwithme.authoring.internal.export

import de.optadata.odil.learnwithme.authoring.internal.domain.BloomLevel
import de.optadata.odil.learnwithme.authoring.internal.domain.Item
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import java.util.UUID

private fun item(type: ItemType, stem: String, payloadJson: String, explanation: String = "Weil.") = Item(
    workspaceId = UUID.randomUUID(),
    conceptId = UUID.randomUUID(),
    type = type,
    stem = stem,
    payload = payloadJson,
    explanation = explanation,
    bloomLevel = BloomLevel.APPLY,
    sourceChunkId = UUID.randomUUID(),
    sourceCharFrom = 0,
    sourceCharTo = 10,
)

class AnkiExporterTest {

    @Test
    fun `one line per item, each with exactly one tab separating front and back`() {
        val items = listOf(
            item(ItemType.TRUE_FALSE, "3NF impliziert 2NF.", """{"statement":"3NF impliziert 2NF.","answer":true,"rationale":"Per Definition."}"""),
            item(ItemType.NUMERIC, "g?", """{"value":9.81,"tolerance":0.1,"unit":"m/s^2"}"""),
        )

        val tsv = AnkiExporter.toTsv(items)
        val lines = tsv.split("\n")

        lines.size shouldBe 2
        lines.forEach { it.count { c -> c == '\t' } shouldBe 1 }
    }

    @Test
    fun `TRUE_FALSE front is the statement, back names the answer`() {
        val tsv = AnkiExporter.toTsv(listOf(item(ItemType.TRUE_FALSE, "ignored", """{"statement":"Die Erde ist rund.","answer":true,"rationale":"Ja."}""")))
        val (front, back) = tsv.split("\t")
        front shouldBe "Die Erde ist rund."
        (back.contains("Wahr") && back.contains("Ja.")) shouldBe true
    }

    @Test
    fun `embedded newlines in a field become br tags so the line count stays one item per line`() {
        val tsv = AnkiExporter.toTsv(
            listOf(item(ItemType.NUMERIC, "g?", """{"value":9.81,"tolerance":0.1,"unit":"m/s^2"}""", explanation = "Zeile 1\nZeile 2")),
        )
        tsv.split("\n").size shouldBe 1
        tsv.contains("<br>") shouldBe true
    }

    @Test
    fun `CATEGORIZATION back groups elements under their bucket label`() {
        val payload = """{"buckets":[{"id":"b1","label":"Fisch"},{"id":"b2","label":"Säugetier"}],
            |"elements":[{"id":"e1","text":"Hai","bucketId":"b1"},{"id":"e2","text":"Wal","bucketId":"b2"}]}""".trimMargin()
        val tsv = AnkiExporter.toTsv(listOf(item(ItemType.CATEGORIZATION, "Ordne zu.", payload)))
        val back = tsv.split("\t")[1]
        (back.contains("Fisch: Hai") && back.contains("Säugetier: Wal")) shouldBe true
    }
}
