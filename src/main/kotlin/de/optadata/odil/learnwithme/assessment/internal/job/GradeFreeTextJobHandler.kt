package de.optadata.odil.learnwithme.assessment.internal.job

import de.optadata.odil.learnwithme.assessment.internal.grading.FreeTextGrade
import de.optadata.odil.learnwithme.assessment.internal.grading.FreeTextGrader
import de.optadata.odil.learnwithme.assessment.internal.grading.RubricCriterionView
import de.optadata.odil.learnwithme.assessment.internal.service.AttemptService
import de.optadata.odil.learnwithme.authoring.AuthoringApi
import de.optadata.odil.learnwithme.platform.JobHandler
import de.optadata.odil.learnwithme.platform.JobType
import de.optadata.odil.learnwithme.shared.JsonMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/** Epic H (E4): das LLM-Rubric-Grading für `SHORT_ANSWER` — der einzige LLM-Call im
 * Antwort-Pfad, deshalb außerhalb des Antwort-kritischen Pfads (N1, §6.5). Ausgelöst von
 * `AttemptService.submit`. */
@Component
class GradeFreeTextJobHandler(
    private val authoringApi: AuthoringApi,
    private val freeTextGrader: FreeTextGrader,
    private val attemptService: AttemptService,
) : JobHandler {

    override val type = JobType.GRADE_FREE_TEXT
    private val log = LoggerFactory.getLogger(GradeFreeTextJobHandler::class.java)
    private val mapper = JsonMapper.instance

    override fun handle(jobId: UUID, payloadJson: String) {
        val node = mapper.readTree(payloadJson)
        val workspaceId = UUID.fromString(node.get("workspaceId").asText())
        val userId = UUID.fromString(node.get("userId").asText())
        val sessionId = UUID.fromString(node.get("sessionId").asText())
        val itemId = UUID.fromString(node.get("itemId").asText())
        val responseJson = node.get("response").asText()
        val elapsedMs = node.get("elapsedMs").asInt()

        val item = authoringApi.getPublished(workspaceId, itemId)
        val payload = mapper.readTree(item.payloadJson)
        val rubric = payload.path("rubric").map { RubricCriterionView(it.path("criterion").asText(), it.path("points").asInt()) }
        val response = mapper.readTree(responseJson).path("answer").asText("")

        try {
            val grade = freeTextGrader.grade(workspaceId, item.stem, rubric, payload.path("referenceAnswer").asText(""), response)
            attemptService.finalizeShortAnswerGrade(workspaceId, userId, sessionId, itemId, responseJson, elapsedMs, grade.copy(feedback = withRubricBreakdown(grade)))
        } catch (ex: Exception) {
            log.warn("Freitext-Grading fehlgeschlagen (Session {}, Item {}): {}", sessionId, itemId, ex.message, ex)
            throw ex
        }
    }

    /** PLAN.md §4.2 E4: „Rubric ist dem Nutzer sichtbar" — statt eines eigenen strukturierten
     * Feldes (weiterer DTO-Ausbau über `assessment` hinweg) hängt das die Kriterium-für-Kriterium-
     * Punktzahlen lesbar vor den LLM-Verbesserungshinweis. */
    private fun withRubricBreakdown(grade: FreeTextGrade): String {
        val breakdown = grade.criterionScores.joinToString(" ") { "${it.criterion}: ${it.awardedPoints}/${it.maxPoints} Punkte." }
        return "$breakdown ${grade.feedback}".trim()
    }
}
