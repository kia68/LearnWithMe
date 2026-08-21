package de.optadata.odil.learnwithme.analytics.internal.service

import de.optadata.odil.learnwithme.analytics.AnalyticsApi
import de.optadata.odil.learnwithme.analytics.ErrorAnalysisView
import de.optadata.odil.learnwithme.analytics.internal.classification.ErrorClassifier
import de.optadata.odil.learnwithme.analytics.internal.config.AnalyticsProperties
import de.optadata.odil.learnwithme.analytics.internal.domain.ErrorCategory
import de.optadata.odil.learnwithme.analytics.internal.domain.ErrorEvent
import de.optadata.odil.learnwithme.analytics.internal.domain.Misconception
import de.optadata.odil.learnwithme.analytics.internal.persistence.ErrorEventRepository
import de.optadata.odil.learnwithme.analytics.internal.persistence.MisconceptionRepository
import de.optadata.odil.learnwithme.platform.JobQueue
import de.optadata.odil.learnwithme.platform.JobType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AnalyticsApiImpl(
    private val errorEventRepository: ErrorEventRepository,
    private val misconceptionRepository: MisconceptionRepository,
    private val jobQueue: JobQueue,
    private val properties: AnalyticsProperties,
) : AnalyticsApi {

    @Transactional
    override fun analyzeError(
        workspaceId: UUID,
        userId: UUID,
        attemptId: Long,
        itemId: UUID,
        conceptId: UUID,
        itemType: String,
        expectedSuccess: Float,
        elapsedMs: Int,
        thetaBefore: Float,
        itemDifficulty: Float,
        chosenOptionMisconceptionCategory: String?,
    ): ErrorAnalysisView {
        val classification = ErrorClassifier.classify(
            itemType, expectedSuccess, elapsedMs, thetaBefore, itemDifficulty, chosenOptionMisconceptionCategory, properties,
        )

        errorEventRepository.save(
            ErrorEvent(
                workspaceId = workspaceId,
                attemptId = attemptId,
                userId = userId,
                conceptId = conceptId,
                itemId = itemId,
                category = classification.category,
                confidence = classification.confidence,
                detectedBy = classification.detectedBy,
            ),
        )

        // CARELESS/AMBIGUOUS_ITEM sind keine Wissenslücken (§11.5: "nicht als Wissenslücke werten"
        // bzw. das Item, nicht der Lernende, ist verdächtig) — fließen bewusst nicht in E3/E6.
        var note: String? = null
        if (classification.category != ErrorCategory.CARELESS && classification.category != ErrorCategory.AMBIGUOUS_ITEM) {
            val misconception = misconceptionRepository.findByUserIdAndConceptIdAndCategory(userId, conceptId, classification.category)
                ?: Misconception(workspaceId = workspaceId, userId = userId, conceptId = conceptId, category = classification.category, occurrences = 0)
            misconception.occurrences += 1
            misconception.lastSeenAt = Instant.now()
            misconceptionRepository.save(misconception)
            if (misconception.occurrences >= properties.misconceptionThreshold) {
                note = "${misconception.occurrences}. Mal in diesem Konzept"
            }

            // E6: idempotent über den jobKey (JobQueue-Vertrag) — pro Original-Item nur einmal.
            jobQueue.enqueue(
                JobType.GENERATE_PARAPHRASE,
                "paraphrase:$itemId",
                mapOf("workspaceId" to workspaceId.toString(), "originalItemId" to itemId.toString()),
            )
        }

        return ErrorAnalysisView(classification.category.name, classification.confidence, note)
    }
}
