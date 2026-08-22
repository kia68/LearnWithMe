package de.optadata.odil.learnwithme.authoring.internal.export

import de.optadata.odil.learnwithme.authoring.internal.domain.CategorizationPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.ClozePayload
import de.optadata.odil.learnwithme.authoring.internal.domain.CodeOutputPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.Item
import de.optadata.odil.learnwithme.authoring.internal.domain.ItemPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.MatchingPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.McMultiPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.McSinglePayload
import de.optadata.odil.learnwithme.authoring.internal.domain.NumericPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.OrderingPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.PayloadCodec
import de.optadata.odil.learnwithme.authoring.internal.domain.ShortAnswerPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.TrueFalsePayload

/**
 * PLAN.md §14 (M6): "Export (QTI/Anki)". Bewusst KEIN `.apkg` (SQLite+Media-Zip, `genanki`-Format) —
 * Ankis eigener Text-Importer (Datei → Importieren) akzeptiert Tab-getrennten Text mit
 * `Front<TAB>Back` pro Zeile direkt, HTML in Feldern eingeschlossen (`<br>` für Zeilenumbrüche).
 * Dieselbe pragmatische Vereinfachung wie an anderer Stelle in dieser Codebase (z.B. `TEXT` statt
 * `JSONB`) — der volle `.apkg`-Binärcontainer wäre für denselben Nutzwert unverhältnismäßig
 * aufwendiger (SQLite-Schema + Media-Referenzen), ohne dass Anki-Nutzer davon profitieren.
 */
object AnkiExporter {

    fun toTsv(items: List<Item>): String =
        items.joinToString("\n") { item ->
            val payload = PayloadCodec.deserialize(item.type, item.payload)
            val (front, back) = frontBack(item, payload)
            "${field(front)}\t${field(back)}"
        }

    /** Ankis Text-Importer trennt Felder an Tabs und Zeilen an Newlines — beides muss also aus
     * Feldinhalten raus; `<br>` statt `\n` funktioniert, weil Anki-Karten HTML rendern. */
    private fun field(text: String): String = text.replace("\r\n", "\n").replace("\n", "<br>").replace("\t", "    ")

    private fun frontBack(item: Item, payload: ItemPayload): Pair<String, String> {
        val (front, answer) = questionAndAnswer(item, payload)
        val back = if (item.explanation.isBlank()) answer else "$answer\n\n${item.explanation}"
        return front to back
    }

    private fun questionAndAnswer(item: Item, payload: ItemPayload): Pair<String, String> = when (payload) {
        is McSinglePayload -> item.stem to payload.options.joinToString("\n") { "${if (it.correct) "✓" else "✗"} ${it.text} — ${it.rationale}" }
        is McMultiPayload -> item.stem to payload.options.joinToString("\n") { "${if (it.correct) "✓" else "✗"} ${it.text} — ${it.rationale}" }
        is TrueFalsePayload -> payload.statement to "${if (payload.answer) "Wahr" else "Falsch"} — ${payload.rationale}"
        is OrderingPayload -> {
            val byId = payload.elements.associateBy { it.id }
            item.stem to payload.correctOrder.mapIndexed { i, id -> "${i + 1}. ${byId[id]?.text ?: id}" }.joinToString("\n")
        }
        is MatchingPayload -> {
            val leftById = payload.left.associateBy { it.id }
            val rightById = (payload.right + payload.distractorsRight).associateBy { it.id }
            item.stem to payload.pairs.joinToString("\n") { "${leftById[it.leftId]?.text ?: it.leftId} → ${rightById[it.rightId]?.text ?: it.rightId}" }
        }
        is ClozePayload -> {
            val front = payload.template.replace(Regex("\\{\\{\\d+}}"), "____")
            val back = payload.blanks.withIndex().fold(payload.template) { acc, (i, blank) ->
                acc.replace("{{${i + 1}}}", blank.accepted.firstOrNull() ?: "")
            }
            front to back
        }
        is ShortAnswerPayload -> item.stem to "${payload.referenceAnswer}\n\nRubric: ${payload.rubric.joinToString("; ") { "${it.criterion} (${it.points})" }}"
        is NumericPayload -> item.stem to "${payload.value} ± ${payload.tolerance}${payload.unit?.let { " $it" } ?: ""}"
        is CategorizationPayload -> {
            val back = payload.buckets.joinToString("\n") { b ->
                "${b.label}: ${payload.elements.filter { it.bucketId == b.id }.joinToString(", ") { it.text }}"
            }
            item.stem to back
        }
        is CodeOutputPayload -> "${item.stem}\n\n${payload.snippet}" to payload.expected
    }
}
