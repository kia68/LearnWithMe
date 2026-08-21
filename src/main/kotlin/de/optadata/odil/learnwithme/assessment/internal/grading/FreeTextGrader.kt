package de.optadata.odil.learnwithme.assessment.internal.grading

import de.optadata.odil.learnwithme.ai.LlmGateway
import de.optadata.odil.learnwithme.ai.LlmTask
import de.optadata.odil.learnwithme.ai.StructuredRequest
import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

data class RubricCriterionView(val criterion: String, val points: Int)

data class CriterionScoreView(val criterion: String, val awardedPoints: Int, val maxPoints: Int)

data class FreeTextGrade(
    val score: Float,
    val outcome: AttemptOutcome,
    val feedback: String,
    val criterionScores: List<CriterionScoreView>,
)

/** Positional statt namensbasiert: das Modell liefert nur Punktzahlen in Rubric-Reihenfolge
 * zurück, nicht den Kriteriumstext erneut — vermeidet brüchiges String-Matching gegen eine vom
 * Modell u.U. leicht abgewandelte Formulierung (PLAN.md A-4: „Structured Output bei kleinen
 * Modellen unzuverlässig"). */
internal data class FreeTextGradeDraft(val awardedPoints: List<Int>, val feedback: String)

/** E4/Epic H: Freitext-gegen-Rubric-Bewertung — der einzige LLM-Call im Antwort-Pfad des neuen
 * Fragetyps, deshalb strikt asynchron (siehe `GradeFreeTextJobHandler`), nie im synchronen
 * `ResponseGrader`-Pfad (N1). */
@Component
class FreeTextGrader(private val llmGateway: LlmGateway) {
    private val log = LoggerFactory.getLogger(FreeTextGrader::class.java)

    fun grade(workspaceId: UUID, stem: String, rubric: List<RubricCriterionView>, referenceAnswer: String, response: String): FreeTextGrade {
        val system = buildString {
            appendLine("Du bewertest die Freitextantwort eines Lernenden gegen ein Rubric, ausschließlich auf Deutsch.")
            appendLine("Antworte NUR mit validem JSON — keine Markdown-Codeblöcke, kein Text vor oder nach dem JSON.")
            appendLine("Vergib für JEDES Rubric-Kriterium (in der gegebenen Reihenfolge) eine ganzzahlige Punktzahl zwischen 0 und dessen Maximum.")
            appendLine("Sei fair: inhaltlich richtige, aber anders formulierte Antworten bekommen volle Punktzahl.")
            appendLine("feedback ist ein kurzer, konkreter Verbesserungshinweis für den Lernenden, kein bloßer Score-Kommentar.")
            appendLine(
                """JSON-Schema: {"awardedPoints": [int, ...], "feedback": string}. """ +
                    "Genau ${rubric.size} Zahlen in derselben Reihenfolge wie die Rubric-Kriterien unten.",
            )
        }
        val user = buildString {
            appendLine("Frage: $stem")
            appendLine("Musterantwort: $referenceAnswer")
            appendLine("Rubric:")
            rubric.forEachIndexed { index, c -> appendLine("${index + 1}. ${c.criterion} (max. ${c.points} Punkte)") }
            appendLine()
            appendLine("Antwort des Lernenden:")
            appendLine("\"\"\"")
            appendLine(response)
            appendLine("\"\"\"")
        }

        val result = llmGateway.complete(StructuredRequest(workspaceId, LlmTask.FREE_TEXT_GRADING, system, user, FreeTextGradeDraft::class.java))
        val draft = result.value

        if (draft.awardedPoints.size != rubric.size) {
            log.warn("FreeTextGrader: erwartete {} Punktzahlen, bekam {} — fehlende zählen als 0", rubric.size, draft.awardedPoints.size)
        }
        val scores = rubric.mapIndexed { index, criterion ->
            val awarded = (draft.awardedPoints.getOrNull(index) ?: 0).coerceIn(0, criterion.points)
            CriterionScoreView(criterion.criterion, awarded, criterion.points)
        }
        val totalMax = rubric.sumOf { it.points }
        val totalAwarded = scores.sumOf { it.awardedPoints }
        val score = if (totalMax == 0) 0f else (totalAwarded.toFloat() / totalMax).coerceIn(0f, 1f)
        return FreeTextGrade(score, outcomeForScore(score), draft.feedback, scores)
    }
}
