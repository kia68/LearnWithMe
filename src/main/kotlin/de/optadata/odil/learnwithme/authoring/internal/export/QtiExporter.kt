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
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * PLAN.md §14 (M6): "Export (QTI/Anki)" — QTI **2.1** `assessmentItem`-XML, ein File pro Item,
 * unverpackt als ZIP (KEIN `imsmanifest.xml`/vollständiges Content-Package, ADR-ähnliche
 * Vereinfachung: die meisten QTI-Importer akzeptieren einzelne `assessmentItem`-XML-Dateien auch
 * ohne Manifest; ein Manifest bräuchte zusätzlich Metadaten über die Ziel-Testzusammenstellung,
 * die es in diesem Datenmodell nicht gibt).
 *
 * Nicht jeder eigene Fragetyp hat eine 1:1-QTI-Entsprechung — bewusste Vereinfachungen, jeweils
 * unten kommentiert: `NUMERIC` verliert die `unit`-Prüfung (QTI 2.1 hat keine eingebaute
 * Einheiten-Semantik), `SHORT_ANSWER` wird als nicht-automatisch-bewertetes `extendedTextInteraction`
 * exportiert (Rubric/Musterantwort landen als Freitext-Hinweis, kein QTI-Rubric-Scoring-Standard
 * hier verwendet), `CODE_OUTPUT` als exakter String-Vergleich (verliert nichts gegenüber dem
 * eigenen Grading, das ist ohnehin exakt) und `CATEGORIZATION` als `matchInteraction` (strukturell
 * identisch zu `MATCHING`).
 */
object QtiExporter {

    fun toZip(items: List<Item>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            items.forEach { item ->
                val payload = PayloadCodec.deserialize(item.type, item.payload)
                zip.putNextEntry(ZipEntry("item_${item.id}.xml"))
                zip.write(assessmentItem(item, payload).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun assessmentItem(item: Item, payload: ItemPayload): String {
        val identifier = "ITEM_${item.id}"
        val title = xml(item.stem.take(120))
        val (responseDeclaration, itemBody, responseProcessing) = when (payload) {
            is McSinglePayload -> choiceInteraction(item, payload.options, single = true)
            is McMultiPayload -> choiceInteraction(item, payload.options, single = false)
            is TrueFalsePayload -> trueFalse(item, payload)
            is OrderingPayload -> ordering(item, payload)
            is MatchingPayload -> matching(
                item.stem,
                payload.left.map { SimpleEntry(it.id, it.text) },
                (payload.right + payload.distractorsRight).map { SimpleEntry(it.id, it.text) },
                payload.pairs.map { PairEntry(it.leftId, it.rightId) },
            )
            is CategorizationPayload -> matching(
                item.stem,
                payload.elements.map { SimpleEntry(it.id, it.text) },
                payload.buckets.map { SimpleEntry(it.id, it.label) },
                payload.elements.map { PairEntry(it.id, it.bucketId) },
            )
            is ClozePayload -> cloze(item, payload)
            is ShortAnswerPayload -> shortAnswer(item, payload)
            is NumericPayload -> numeric(item, payload)
            is CodeOutputPayload -> codeOutput(item, payload)
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<assessmentItem xmlns="http://www.imsglobal.org/xsd/imsqti_v2p1"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.imsglobal.org/xsd/imsqti_v2p1 http://www.imsglobal.org/xsd/qti/qtiv2p1/imsqti_v2p1.xsd"
    identifier="$identifier" title="$title" adaptive="false" timeDependent="false">
$responseDeclaration
  <outcomeDeclaration identifier="SCORE" cardinality="single" baseType="float">
    <defaultValue><value>0</value></defaultValue>
  </outcomeDeclaration>
  <itemBody>
$itemBody
  </itemBody>
$responseProcessing
</assessmentItem>
"""
    }

    private data class SimpleEntry(val id: String, val text: String)
    private data class PairEntry(val leftId: String, val rightId: String)

    private const val MATCH_CORRECT = """http://www.imsglobal.org/question/qti_v2p1/rptemplates/match_correct"""

    private fun choiceInteraction(item: Item, options: List<de.optadata.odil.learnwithme.authoring.internal.domain.Option>, single: Boolean):
        Triple<String, String, String> {
        val cardinality = if (single) "single" else "multiple"
        val correctIds = options.withIndex().filter { (_, o) -> o.correct }.map { (i, _) -> "C$i" }
        val responseDeclaration = """
  <responseDeclaration identifier="RESPONSE" cardinality="$cardinality" baseType="identifier">
    <correctResponse>
${correctIds.joinToString("\n") { "      <value>$it</value>" }}
    </correctResponse>
  </responseDeclaration>""".trimStart('\n')
        val choices = options.withIndex().joinToString("\n") { (i, o) -> "      <simpleChoice identifier=\"C$i\">${xml(o.text)}</simpleChoice>" }
        val maxChoices = if (single) "1" else "0"
        val itemBody = """    <p>${xml(item.stem)}</p>
    <choiceInteraction responseIdentifier="RESPONSE" shuffle="false" maxChoices="$maxChoices">
$choices
    </choiceInteraction>"""
        return Triple(responseDeclaration, itemBody, matchCorrectProcessing())
    }

    private fun trueFalse(item: Item, payload: TrueFalsePayload): Triple<String, String, String> {
        val correct = if (payload.answer) "TRUE" else "FALSE"
        val responseDeclaration = """  <responseDeclaration identifier="RESPONSE" cardinality="single" baseType="identifier">
    <correctResponse><value>$correct</value></correctResponse>
  </responseDeclaration>"""
        val itemBody = """    <p>${xml(payload.statement)}</p>
    <choiceInteraction responseIdentifier="RESPONSE" shuffle="false" maxChoices="1">
      <simpleChoice identifier="TRUE">Wahr</simpleChoice>
      <simpleChoice identifier="FALSE">Falsch</simpleChoice>
    </choiceInteraction>"""
        return Triple(responseDeclaration, itemBody, matchCorrectProcessing())
    }

    private fun ordering(item: Item, payload: OrderingPayload): Triple<String, String, String> {
        val responseDeclaration = """  <responseDeclaration identifier="RESPONSE" cardinality="ordered" baseType="identifier">
    <correctResponse>
${payload.correctOrder.joinToString("\n") { "      <value>${xml(it)}</value>" }}
    </correctResponse>
  </responseDeclaration>"""
        val choices = payload.elements.joinToString("\n") { "      <simpleChoice identifier=\"${xml(it.id)}\">${xml(it.text)}</simpleChoice>" }
        val itemBody = """    <p>${xml(item.stem)}</p>
    <orderInteraction responseIdentifier="RESPONSE" shuffle="false">
$choices
    </orderInteraction>"""
        return Triple(responseDeclaration, itemBody, matchCorrectProcessing())
    }

    private fun matching(stem: String, left: List<SimpleEntry>, right: List<SimpleEntry>, pairs: List<PairEntry>): Triple<String, String, String> {
        val responseDeclaration = """  <responseDeclaration identifier="RESPONSE" cardinality="multiple" baseType="directedPair">
    <correctResponse>
${pairs.joinToString("\n") { "      <value>${xml(it.leftId)} ${xml(it.rightId)}</value>" }}
    </correctResponse>
  </responseDeclaration>"""
        val leftSet = left.joinToString("\n") { "      <simpleAssociableChoice identifier=\"${xml(it.id)}\" matchMax=\"1\">${xml(it.text)}</simpleAssociableChoice>" }
        val rightSet = right.joinToString("\n") { "      <simpleAssociableChoice identifier=\"${xml(it.id)}\" matchMax=\"0\">${xml(it.text)}</simpleAssociableChoice>" }
        val itemBody = """    <p>${xml(stem)}</p>
    <matchInteraction responseIdentifier="RESPONSE" shuffle="false" maxAssociations="0">
    <simpleMatchSet>
$leftSet
    </simpleMatchSet>
    <simpleMatchSet>
$rightSet
    </simpleMatchSet>
    </matchInteraction>"""
        return Triple(responseDeclaration, itemBody, matchCorrectProcessing())
    }

    /** Vereinfachung: pro Lücke der ERSTE akzeptierte Wert als `correctResponse` — QTIs
     * `match_correct`-Template kennt kein "eine von mehreren akzeptierten Antworten"-Konzept wie
     * unser eigener `ResponseGrader.gradeCloze`. */
    private fun cloze(item: Item, payload: ClozePayload): Triple<String, String, String> {
        val declarations = payload.blanks.withIndex().joinToString("\n") { (i, blank) ->
            """  <responseDeclaration identifier="RESPONSE_${i + 1}" cardinality="single" baseType="string">
    <correctResponse><value>${xml(blank.accepted.first())}</value></correctResponse>
  </responseDeclaration>"""
        }
        var body = xml(payload.template)
        payload.blanks.indices.forEach { i ->
            body = body.replace("{{${i + 1}}}", "<textEntryInteraction responseIdentifier=\"RESPONSE_${i + 1}\" expectedLength=\"15\"/>")
        }
        val itemBody = "    <p>$body</p>"
        val outcomeAdds = payload.blanks.indices.joinToString("\n") { i ->
            """      <responseCondition>
        <responseIf>
          <match><variable identifier="RESPONSE_${i + 1}"/><correct identifier="RESPONSE_${i + 1}"/></match>
          <setOutcomeValue identifier="SCORE"><sum><variable identifier="SCORE"/><baseValue baseType="float">${1.0 / payload.blanks.size}</baseValue></sum></setOutcomeValue>
        </responseIf>
      </responseCondition>"""
        }
        val responseProcessing = "  <responseProcessing>\n$outcomeAdds\n  </responseProcessing>"
        return Triple(declarations, itemBody, responseProcessing)
    }

    /** `SHORT_ANSWER` ist LLM-Rubric-bewertet (Epic H) — QTI kennt kein äquivalentes automatisches
     * Scoring-Modell dafür. Export als `extendedTextInteraction` ohne `correctResponse`; Rubric +
     * Musterantwort landen als `<rubricBlock>` für menschliche Prüfer im Ziel-System. */
    private fun shortAnswer(item: Item, payload: ShortAnswerPayload): Triple<String, String, String> {
        val responseDeclaration = """  <responseDeclaration identifier="RESPONSE" cardinality="single" baseType="string"/>"""
        val rubricText = payload.rubric.joinToString("; ") { "${xml(it.criterion)} (${it.points} Pkt.)" }
        val itemBody = """    <p>${xml(item.stem)}</p>
    <extendedTextInteraction responseIdentifier="RESPONSE" expectedLength="500"/>
    <rubricBlock view="scorer">
      <p>Musterantwort: ${xml(payload.referenceAnswer)}</p>
      <p>Rubric: $rubricText</p>
    </rubricBlock>"""
        return Triple(responseDeclaration, itemBody, "  <responseProcessing/>")
    }

    /** Verliert die `unit`-Prüfung (kein QTI-2.1-Äquivalent) — die Einheit wird stattdessen im
     * Aufgabentext genannt. Toleranzbereich als expliziter `<gte>`/`<lte>`-Vergleich statt eines
     * Templates, weil QTIs Standard-Templates keine Toleranz kennen. */
    private fun numeric(item: Item, payload: NumericPayload): Triple<String, String, String> {
        val min = payload.value - payload.tolerance
        val max = payload.value + payload.tolerance
        val responseDeclaration = """  <responseDeclaration identifier="RESPONSE" cardinality="single" baseType="float">
    <correctResponse><value>${payload.value}</value></correctResponse>
  </responseDeclaration>"""
        val unitHint = payload.unit?.let { " (${xml(it)})" } ?: ""
        val itemBody = """    <p>${xml(item.stem)}$unitHint</p>
    <textEntryInteraction responseIdentifier="RESPONSE" expectedLength="10"/>"""
        val responseProcessing = """  <responseProcessing>
    <responseCondition>
      <responseIf>
        <and>
          <gte><variable identifier="RESPONSE"/><baseValue baseType="float">$min</baseValue></gte>
          <lte><variable identifier="RESPONSE"/><baseValue baseType="float">$max</baseValue></lte>
        </and>
        <setOutcomeValue identifier="SCORE"><baseValue baseType="float">1</baseValue></setOutcomeValue>
      </responseIf>
    </responseCondition>
  </responseProcessing>"""
        return Triple(responseDeclaration, itemBody, responseProcessing)
    }

    private fun codeOutput(item: Item, payload: CodeOutputPayload): Triple<String, String, String> {
        val responseDeclaration = """  <responseDeclaration identifier="RESPONSE" cardinality="single" baseType="string">
    <correctResponse><value>${xml(payload.expected)}</value></correctResponse>
  </responseDeclaration>"""
        val itemBody = """    <p>${xml(item.stem)}</p>
    <pre>${xml(payload.snippet)}</pre>
    <textEntryInteraction responseIdentifier="RESPONSE" expectedLength="20"/>"""
        return Triple(responseDeclaration, itemBody, matchCorrectProcessing())
    }

    private fun matchCorrectProcessing(): String =
        """  <responseProcessing template="$MATCH_CORRECT"/>"""

    /** Minimalste XML-Escaping — die einzigen fünf Zeichen, die in QTI-Textinhalten/-Attributen
     * problematisch sind. Kein XML-Bibliotheks-Overhead für so wenig Logik. */
    private fun xml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
