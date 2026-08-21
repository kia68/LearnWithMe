package de.optadata.odil.learnwithme.content.internal.extraction

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class SectionBuilderTest {

    @Test
    fun `plain paragraphs without headings yield no sections`() {
        val result = SectionBuilder.build("<p>Erster Absatz.</p><p>Zweiter Absatz.</p>")

        result.sections.size shouldBe 0
        result.plainText shouldContain "Erster Absatz."
        result.plainText shouldContain "Zweiter Absatz."
    }

    @Test
    fun `headings become sections in document order with correct levels`() {
        val html = "<h1>Kapitel 1</h1><p>Intro.</p><h2>Abschnitt 1.1</h2><p>Detail.</p><h1>Kapitel 2</h1><p>Mehr.</p>"

        val result = SectionBuilder.build(html)

        result.sections.map { it.title } shouldBe listOf("Kapitel 1", "Abschnitt 1.1", "Kapitel 2")
        result.sections.map { it.level } shouldBe listOf(1, 2, 1)
        result.sections.map { it.ordinal } shouldBe listOf(0, 1, 2)
    }

    @Test
    fun `section charFrom points at the heading title inside the plain text`() {
        val html = "<p>Vorwort.</p><h1>Erstes Kapitel</h1><p>Inhalt.</p>"

        val result = SectionBuilder.build(html)

        val section = result.sections.single()
        result.plainText.substring(section.charFrom, section.charFrom + section.title.length) shouldBe section.title
    }
}
