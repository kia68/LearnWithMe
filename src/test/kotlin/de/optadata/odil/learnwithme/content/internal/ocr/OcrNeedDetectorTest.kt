package de.optadata.odil.learnwithme.content.internal.ocr

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OcrNeedDetectorTest {

    @Test
    fun `low text density below threshold needs OCR`() {
        // 10 Seiten, nur 50 Zeichen insgesamt -> 5 Zeichen/Seite, Schwelle 100.
        OcrNeedDetector.needsOcr(extractedTextLength = 50, pageCount = 10, thresholdCharsPerPage = 100) shouldBe true
    }

    @Test
    fun `sufficient text density does not need OCR`() {
        OcrNeedDetector.needsOcr(extractedTextLength = 5000, pageCount = 10, thresholdCharsPerPage = 100) shouldBe false
    }

    @Test
    fun `unknown page count never triggers OCR`() {
        OcrNeedDetector.needsOcr(extractedTextLength = 0, pageCount = null, thresholdCharsPerPage = 100) shouldBe false
    }

    @Test
    fun `zero page count never triggers OCR`() {
        OcrNeedDetector.needsOcr(extractedTextLength = 0, pageCount = 0, thresholdCharsPerPage = 100) shouldBe false
    }
}
