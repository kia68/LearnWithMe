package de.optadata.odil.learnwithme.analytics.internal.classification

import de.optadata.odil.learnwithme.analytics.internal.config.AnalyticsProperties
import de.optadata.odil.learnwithme.analytics.internal.domain.DetectionMethod
import de.optadata.odil.learnwithme.analytics.internal.domain.ErrorCategory

data class Classification(val category: ErrorCategory, val confidence: Float, val detectedBy: DetectionMethod)

/**
 * E1-Klassifikation (§11.5) — reine Funktion, kein LLM, kein I/O. Reihenfolge der Prüfungen ist
 * Absicht: `AMBIGUOUS_ITEM` und `CARELESS` sind reine Attempt-Heuristiken und gehen der
 * Distraktor-Tag-Auswertung vor, weil sie unabhängig vom gewählten Item-Typ gelten.
 */
object ErrorClassifier {

    /** [correct] muss vor dem Aufruf ausgeschlossen sein — korrekte Antworten erzeugen kein
     * [Classification] (Aufrufer gibt dafür `null` zurück, siehe `ErrorAnalysisService`). */
    fun classify(
        itemType: String,
        expectedSuccess: Float,
        elapsedMs: Int,
        thetaBefore: Float,
        itemDifficulty: Float,
        chosenOptionMisconceptionCategory: String?,
        properties: AnalyticsProperties,
    ): Classification {
        if (thetaBefore > itemDifficulty + properties.ambiguousThetaMargin) {
            return Classification(ErrorCategory.AMBIGUOUS_ITEM, 0.7f, DetectionMethod.HEURISTIC)
        }
        if (expectedSuccess >= properties.carelessMinExpectedSuccess && elapsedMs in 1 until properties.carelessMaxElapsedMs) {
            return Classification(ErrorCategory.CARELESS, 0.6f, DetectionMethod.HEURISTIC)
        }
        val tagged = chosenOptionMisconceptionCategory?.let { raw -> runCatching { ErrorCategory.valueOf(raw) }.getOrNull() }
        if (tagged != null && tagged != ErrorCategory.CARELESS && tagged != ErrorCategory.AMBIGUOUS_ITEM) {
            return Classification(tagged, 0.9f, DetectionMethod.RATIONALE)
        }
        return when (itemType) {
            "ORDERING", "MATCHING", "CLOZE" -> Classification(ErrorCategory.PROCEDURAL, 0.5f, DetectionMethod.HEURISTIC)
            else -> Classification(ErrorCategory.FACTUAL_GAP, 0.4f, DetectionMethod.HEURISTIC)
        }
    }
}
