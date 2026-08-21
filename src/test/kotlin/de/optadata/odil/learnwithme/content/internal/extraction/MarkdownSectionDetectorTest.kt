package de.optadata.odil.learnwithme.content.internal.extraction

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MarkdownSectionDetectorTest {

    @Test
    fun `detects ATX headings with correct level and title`() {
        val text = """
            # Titel
            Etwas Text.

            ## Unterabschnitt
            Mehr Text.

            ###### Tief verschachtelt
        """.trimIndent()

        val sections = MarkdownSectionDetector.detect(text)

        sections.map { it.level } shouldBe listOf(1, 2, 6)
        sections.map { it.title } shouldBe listOf("Titel", "Unterabschnitt", "Tief verschachtelt")
    }

    @Test
    fun `text without ATX headings yields no sections`() {
        MarkdownSectionDetector.detect("Nur normaler Text.\nNoch eine Zeile.").size shouldBe 0
    }

    @Test
    fun `hash without following space is not a heading`() {
        MarkdownSectionDetector.detect("#nicht-heading").size shouldBe 0
    }
}
