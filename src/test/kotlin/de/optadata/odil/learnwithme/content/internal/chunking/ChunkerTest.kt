package de.optadata.odil.learnwithme.content.internal.chunking

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class ChunkerTest {

    @Test
    fun `single short paragraph yields one chunk covering the whole text`() {
        val text = "Ein kurzer Absatz ohne weitere Struktur."

        val chunks = Chunker(targetTokens = 512, overlapTokens = 64).chunk(text)

        chunks.size shouldBe 1
        chunks[0].text shouldBe text
        chunks[0].charFrom shouldBe 0
        chunks[0].charTo shouldBe text.length
    }

    @Test
    fun `blank text yields no chunks`() {
        Chunker(targetTokens = 512, overlapTokens = 64).chunk("   ").size shouldBe 0
    }

    @Test
    fun `offsets always reconstruct the exact original substring`() {
        val text = (1..40).joinToString("\n\n") { "Absatz Nummer $it mit ein wenig zusätzlichem Text für Länge." }

        val chunks = Chunker(targetTokens = 50, overlapTokens = 10).chunk(text)

        chunks.forEach { chunk -> text.substring(chunk.charFrom, chunk.charTo) shouldBe chunk.text }
    }

    @Test
    fun `many paragraphs are split into multiple chunks with overlap`() {
        val text = (1..40).joinToString("\n\n") { "Absatz Nummer $it mit ein wenig zusätzlichem Text für Länge." }

        val chunks = Chunker(targetTokens = 50, overlapTokens = 10).chunk(text)

        (chunks.size > 1) shouldBe true
        // Aufeinanderfolgende Chunks überlappen: der zweite beginnt vor dem Ende des ersten.
        chunks[1].charFrom shouldBe (chunks[1].charFrom.coerceAtMost(chunks[0].charTo))
    }

    @Test
    fun `an overlong paragraph without blank lines is hard-split at word boundaries`() {
        val text = (1..500).joinToString(" ") { "wort$it" }

        val chunks = Chunker(targetTokens = 50, overlapTokens = 5).chunk(text)

        (chunks.size > 1) shouldBe true
        chunks.forEach { it.text shouldContain "wort" }
    }
}
