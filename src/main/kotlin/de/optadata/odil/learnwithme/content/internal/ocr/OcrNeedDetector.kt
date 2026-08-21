package de.optadata.odil.learnwithme.content.internal.ocr

/** B5: erkennt gescannte PDFs an geringer Textdichte statt an Dateiformat allein —
 * ein durchsuchbares PDF mit wenig Text pro Seite (z.B. eine Titelseite) soll nicht
 * fälschlich als OCR-bedürftig markiert werden, daher der Schwellwert pro Seite. */
object OcrNeedDetector {
    fun needsOcr(extractedTextLength: Int, pageCount: Int?, thresholdCharsPerPage: Int): Boolean {
        if (pageCount == null || pageCount <= 0) return false
        val avgCharsPerPage = extractedTextLength.toDouble() / pageCount
        return avgCharsPerPage < thresholdCharsPerPage
    }
}
