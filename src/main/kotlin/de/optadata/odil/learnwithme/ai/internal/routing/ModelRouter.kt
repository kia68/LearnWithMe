package de.optadata.odil.learnwithme.ai.internal.routing

import de.optadata.odil.learnwithme.ai.LlmTask
import org.springframework.stereotype.Component

/** ADR-010: bildet Task-Klassen auf Provider/Modell ab. Reine Konfigurationsauskunft —
 * pro-Tenant-Override existiert noch nicht (kein Story-Bedarf in Epic C), siehe docs/progress.md. */
@Component
class ModelRouter(private val routing: AiRoutingProperties) {

    fun resolve(task: LlmTask): AiRoute = when (task) {
        LlmTask.CONCEPT_EXTRACTION -> routing.conceptExtraction
        LlmTask.EMBEDDING -> routing.embedding
        LlmTask.ITEM_GENERATION -> routing.itemGeneration
        LlmTask.GROUNDEDNESS_JUDGE -> routing.groundednessJudge
        LlmTask.FREE_TEXT_GRADING -> routing.freeTextGrading
        LlmTask.ERROR_ANALYSIS -> routing.errorAnalysis
    }
}
