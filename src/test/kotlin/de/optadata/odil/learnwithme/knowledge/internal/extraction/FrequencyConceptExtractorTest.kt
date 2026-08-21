package de.optadata.odil.learnwithme.knowledge.internal.extraction

import de.optadata.odil.learnwithme.content.ChunkView
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlin.test.Test

class FrequencyConceptExtractorTest {

    @Test
    fun `frequently repeated term becomes a concept with correct frequency`() {
        val chunkId = UUID.randomUUID()
        val chunks = listOf(
            ChunkView(chunkId, 0, "Photosynthese ist wichtig. Photosynthese passiert in Chloroplasten. Photosynthese braucht Licht."),
        )

        val concepts = FrequencyConceptExtractor.extract(chunks, minOccurrences = 2)

        val photosynthesis = concepts.first { it.name == "Photosynthese" }
        photosynthesis.frequency shouldBe 3
        photosynthesis.evidenceChunkIds shouldBe listOf(chunkId)
    }

    @Test
    fun `evidence spans multiple chunks the term appears in`() {
        val chunkA = ChunkView(UUID.randomUUID(), 0, "Mitochondrien erzeugen Energie für die Zelle.")
        val chunkB = ChunkView(UUID.randomUUID(), 1, "Mitochondrien haben eine eigene DNA.")

        val concepts = FrequencyConceptExtractor.extract(listOf(chunkA, chunkB), minOccurrences = 2)

        val mito = concepts.first { it.name == "Mitochondrien" }
        mito.evidenceChunkIds shouldBe listOf(chunkA.id, chunkB.id)
    }

    @Test
    fun `terms below the occurrence threshold are dropped`() {
        val chunk = ChunkView(UUID.randomUUID(), 0, "Einmaliges Wort taucht nur hier auf.")

        val concepts = FrequencyConceptExtractor.extract(listOf(chunk), minOccurrences = 2)

        concepts.any { it.name == "Einmaliges" } shouldBe false
    }

    @Test
    fun `common stopwords never become concepts regardless of frequency`() {
        val chunk = ChunkView(UUID.randomUUID(), 0, "und und und der der der ist ist ist")

        val concepts = FrequencyConceptExtractor.extract(listOf(chunk), minOccurrences = 2)

        concepts.size shouldBe 0
    }

    @Test
    fun `result is capped at maxConcepts, ranked by frequency`() {
        // Eigene Buchstaben-Suffixe statt Ziffern: das Wortmuster (nur \p{L}) bricht an der ersten
        // Ziffer ab, ein numerisches Suffix würde also fälschlich alle auf denselben Stamm falten.
        val words = ('a'..'t').map { letter -> "begriff$letter" } // 20 unterschiedliche Wörter
        val text = words.joinToString(" ") { "$it $it" } // je 2x
        val chunk = ChunkView(UUID.randomUUID(), 0, text)

        val concepts = FrequencyConceptExtractor.extract(listOf(chunk), maxConcepts = 5, minOccurrences = 2)

        concepts.size shouldBe 5
    }
}
