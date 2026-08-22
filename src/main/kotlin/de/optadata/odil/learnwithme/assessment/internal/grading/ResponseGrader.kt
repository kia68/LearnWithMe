package de.optadata.odil.learnwithme.assessment.internal.grading

import com.fasterxml.jackson.databind.JsonNode
import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome
import de.optadata.odil.learnwithme.shared.JsonMapper
import org.springframework.stereotype.Component
import kotlin.math.max

/**
 * Deterministisches Grading pro Fragetyp (§10.2/10.3). Bewusst OHNE Abhängigkeit auf
 * `authoring.internal.domain.ItemPayload` — die Modulgrenze (§6.3) erlaubt `assessment` nur den
 * öffentlichen `AuthoringApi`-Port, der den Payload als rohen JSON-String liefert (ADR-007-Analogon:
 * Typ-Dispatch über den `type`-String statt über einen Kotlin-Typ, der die Grenze überqueren müsste).
 * Kein LLM im Pfad (N1) — deshalb bewusst OHNE SHORT_ANSWER (Epic H): dessen Rubric-Bewertung
 * braucht zwingend einen LLM-Call und läuft daher asynchron über
 * assessment.internal.job.GradeFreeTextJobHandler, nicht hier — AttemptService.submit verzweigt
 * vor diesem Grader dorthin.
 */
@Component
class ResponseGrader {
    private val mapper = JsonMapper.instance

    fun grade(type: String, payloadJson: String, responseJson: JsonNode): GradeResult {
        val payload = mapper.readTree(payloadJson)
        return when (type) {
            "MC_SINGLE" -> gradeMcSingle(payload, responseJson)
            "MC_MULTI" -> gradeMcMulti(payload, responseJson)
            "TRUE_FALSE" -> gradeTrueFalse(payload, responseJson)
            "ORDERING" -> gradeOrdering(payload, responseJson)
            "MATCHING" -> gradeMatching(payload, responseJson)
            "CLOZE" -> gradeCloze(payload, responseJson)
            "NUMERIC" -> gradeNumeric(payload, responseJson)
            "CATEGORIZATION" -> gradeCategorization(payload, responseJson)
            "CODE_OUTPUT" -> gradeCodeOutput(payload, responseJson)
            else -> error("Unbekannter Fragetyp beim Grading: $type")
        }
    }

    private fun outcomeFor(score: Float): AttemptOutcome = outcomeForScore(score)

    private fun options(payload: JsonNode): List<JsonNode> = payload.path("options").toList()

    private fun gradeMcSingle(payload: JsonNode, response: JsonNode): GradeResult {
        val opts = options(payload)
        val correct = opts.firstOrNull { it.path("correct").asBoolean(false) }
        val chosenId = response.path("optionId").asText(null)
        val chosen = opts.firstOrNull { it.path("id").asText() == chosenId }
        val score = if (chosen != null && chosen === correct) 1f else 0f
        val correctJson = mapper.writeValueAsString(mapOf("optionId" to correct?.path("id")?.asText()))
        val misconceptionCategory = if (score < 1f) chosen?.path("misconceptionCategory")?.asText(null) else null
        return GradeResult(score, outcomeFor(score), correctJson, chosen?.path("rationale")?.asText(), misconceptionCategory)
    }

    private fun gradeMcMulti(payload: JsonNode, response: JsonNode): GradeResult {
        val opts = options(payload)
        val correctIds = opts.filter { it.path("correct").asBoolean(false) }.map { it.path("id").asText() }.toSet()
        val chosenIds = response.path("optionIds").map { it.asText() }.toSet()
        val truePositives = chosenIds.intersect(correctIds).size
        val falsePositives = (chosenIds - correctIds).size
        val score = if (correctIds.isEmpty()) 0f else max(0f, (truePositives - falsePositives).toFloat() / correctIds.size)
        val rationaleText = opts.filter { it.path("id").asText() in chosenIds }
            .joinToString("; ") { it.path("rationale").asText() }
        val rationale = if (rationaleText.isBlank()) null else rationaleText
        val falsePositiveTag = opts.firstOrNull { it.path("id").asText() in chosenIds && it.path("id").asText() !in correctIds }
            ?.path("misconceptionCategory")?.asText(null)
        val correctJson = mapper.writeValueAsString(mapOf("optionIds" to correctIds.toList()))
        return GradeResult(score, outcomeFor(score), correctJson, rationale, falsePositiveTag)
    }

    private fun gradeTrueFalse(payload: JsonNode, response: JsonNode): GradeResult {
        val correctAnswer = payload.path("answer").asBoolean()
        val chosen = response.path("answer").asBoolean(!correctAnswer)
        val score = if (chosen == correctAnswer) 1f else 0f
        val correctJson = mapper.writeValueAsString(mapOf("answer" to correctAnswer))
        return GradeResult(score, outcomeFor(score), correctJson, payload.path("rationale").asText(null))
    }

    private fun gradeOrdering(payload: JsonNode, response: JsonNode): GradeResult {
        val correctOrder = payload.path("correctOrder").map { it.asText() }
        val partialCredit = payload.path("partialCredit").asBoolean(true)
        val responseOrder = response.path("order").map { it.asText() }
        val score = if (!partialCredit) {
            if (responseOrder == correctOrder) 1f else 0f
        } else {
            kendallTauScore(correctOrder, responseOrder)
        }
        val correctJson = mapper.writeValueAsString(mapOf("order" to correctOrder))
        return GradeResult(score, outcomeFor(score), correctJson, null)
    }

    /** Anteil konkordanter Paare zwischen `correctOrder` und der Antwort — normalisiertes
     * Kendall-Tau auf [0,1] (§10.3). Unvollständige/ungültige Antworten (fehlende oder doppelte
     * Elemente) zählen als 0. */
    private fun kendallTauScore(correctOrder: List<String>, responseOrder: List<String>): Float {
        val n = correctOrder.size
        if (n < 2 || responseOrder.size != n || responseOrder.toSet() != correctOrder.toSet()) return 0f
        val positionInResponse = responseOrder.withIndex().associate { (idx, id) -> id to idx }
        var concordant = 0
        var total = 0
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                total++
                if (positionInResponse.getValue(correctOrder[i]) < positionInResponse.getValue(correctOrder[j])) concordant++
            }
        }
        return concordant.toFloat() / total
    }

    private fun gradeMatching(payload: JsonNode, response: JsonNode): GradeResult {
        val correctPairs = payload.path("pairs").associate { it.path("leftId").asText() to it.path("rightId").asText() }
        val responsePairs = response.path("pairs").associate { it.path("leftId").asText() to it.path("rightId").asText() }
        val matched = correctPairs.count { (leftId, rightId) -> responsePairs[leftId] == rightId }
        val score = if (correctPairs.isEmpty()) 0f else matched.toFloat() / correctPairs.size
        val correctJson = mapper.writeValueAsString(mapOf("pairs" to correctPairs.map { (l, r) -> mapOf("leftId" to l, "rightId" to r) }))
        return GradeResult(score, outcomeFor(score), correctJson, null)
    }

    private fun gradeCloze(payload: JsonNode, response: JsonNode): GradeResult {
        val blanks = payload.path("blanks")
        val accepted = blanks.map { blank -> blank.path("accepted").map { normalize(it.asText()) }.toSet() }
        val answers = response.path("answers").map { it.asText() }
        val correctCount = accepted.indices.count { i -> answers.getOrNull(i)?.let { normalize(it) in accepted[i] } == true }
        val score = if (accepted.isEmpty()) 0f else correctCount.toFloat() / accepted.size
        val correctJson = mapper.writeValueAsString(mapOf("answers" to blanks.map { it.path("accepted").firstOrNull()?.asText() }))
        return GradeResult(score, outcomeFor(score), correctJson, null)
    }

    private fun normalize(text: String): String = text.trim().lowercase()

    /** Binär, keine Teilpunkte (§10.3 listet NUMERIC nicht unter „Teilpunkte") — „ungefähr richtig"
     * ist bei einer Zahl kein sinnvolles Zwischenergebnis, anders als bei MC_MULTI/ORDERING/etc. */
    private fun gradeNumeric(payload: JsonNode, response: JsonNode): GradeResult {
        val expectedValue = payload.path("value").asDouble()
        val tolerance = payload.path("tolerance").asDouble()
        val expectedUnit = payload.path("unit").takeIf { it.isTextual }?.asText()
        val responseValue = response.path("answer").let { if (it.isMissingNode || it.isNull) Double.NaN else it.asDouble(Double.NaN) }
        val responseUnit = response.path("unit").asText(null)
        val withinTolerance = !responseValue.isNaN() && kotlin.math.abs(responseValue - expectedValue) <= tolerance
        val unitOk = expectedUnit.isNullOrBlank() || responseUnit?.trim()?.equals(expectedUnit.trim(), ignoreCase = true) == true
        val score = if (withinTolerance && unitOk) 1f else 0f
        val correctJson = mapper.writeValueAsString(mapOf("answer" to expectedValue, "unit" to expectedUnit))
        return GradeResult(score, outcomeFor(score), correctJson, null)
    }

    /** Teilpunkte analog MATCHING (§10.3 nennt CATEGORIZATION nicht explizit, aber strukturell
     * dieselbe „ordne jedes Element korrekt zu"-Aufgabe wie MATCHING). */
    private fun gradeCategorization(payload: JsonNode, response: JsonNode): GradeResult {
        val correctAssignments = payload.path("elements").associate { it.path("id").asText() to it.path("bucketId").asText() }
        val responseAssignments = response.path("assignments").associate { it.path("elementId").asText() to it.path("bucketId").asText() }
        val matched = correctAssignments.count { (elementId, bucketId) -> responseAssignments[elementId] == bucketId }
        val score = if (correctAssignments.isEmpty()) 0f else matched.toFloat() / correctAssignments.size
        val correctJson = mapper.writeValueAsString(mapOf("assignments" to correctAssignments.map { (e, b) -> mapOf("elementId" to e, "bucketId" to b) }))
        return GradeResult(score, outcomeFor(score), correctJson, null)
    }

    /** Exakter (nur getrimmter) Vergleich, KEINE Kleinschreibungs-Normalisierung wie bei CLOZE —
     * Code-Ausgabe ist oft groß-/kleinschreibungssensitiv (siehe `CodeOutputPayload`-Kommentar). */
    private fun gradeCodeOutput(payload: JsonNode, response: JsonNode): GradeResult {
        val expected = payload.path("expected").asText().trim()
        val answer = response.path("answer").asText("").trim()
        val score = if (answer == expected) 1f else 0f
        val correctJson = mapper.writeValueAsString(mapOf("answer" to expected))
        return GradeResult(score, outcomeFor(score), correctJson, null)
    }
}
