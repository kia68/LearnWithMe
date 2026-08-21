package de.optadata.odil.learnwithme.knowledge.internal.extraction

import de.optadata.odil.learnwithme.content.ChunkView
import java.util.UUID

data class ExtractedConcept(val name: String, val frequency: Int, val summary: String, val evidenceChunkIds: List<UUID>)

/**
 * B8: heuristische, LLM-freie Konzeptextraktion (Frequenz + Belegstellen). Zählt wiederkehrende,
 * nicht-triviale Wörter über alle Chunks eines Dokuments — kein semantisches Verständnis, aber
 * deterministisch, kostenlos und ohne Provider-Abhängigkeit lauffähig, sobald ein Dokument indiziert
 * ist. Eine semantische, LLM-gestützte Extraktion ist für M1 vorgesehen, sobald das `LlmGateway`
 * (ADR-004) existiert — bis dahin erfüllt diese Heuristik den in B8 geforderten
 * "Häufigkeit + Belegstellen"-Teil bereits korrekt.
 */
object FrequencyConceptExtractor {

    private val stopwords = setOf(
        "the", "and", "for", "are", "that", "this", "with", "from", "have", "has", "was", "were", "will",
        "into", "also", "such", "than", "then", "been", "being", "their", "which", "would", "could",
        "der", "die", "das", "und", "oder", "für", "mit", "von", "aus", "auf", "ist", "sind", "war", "waren",
        "wird", "werden", "auch", "eine", "einen", "einer", "eines", "nicht", "aber", "wenn", "dann", "dass",
        "sich", "sein", "ihre", "ihrer", "ihren", "kann", "können", "muss", "müssen", "sowie", "zwischen",
    )
    private val wordPattern = Regex("[\\p{L}][\\p{L}'-]{3,}")

    fun extract(chunks: List<ChunkView>, maxConcepts: Int = 15, minOccurrences: Int = 2): List<ExtractedConcept> {
        val occurrences = linkedMapOf<String, MutableList<Pair<UUID, String>>>()

        for (chunk in chunks) {
            for (match in wordPattern.findAll(chunk.text)) {
                val word = match.value
                val key = word.lowercase()
                if (key in stopwords) continue
                occurrences.getOrPut(key) { mutableListOf() }.add(chunk.id to word)
            }
        }

        return occurrences.entries
            .filter { it.value.size >= minOccurrences }
            .sortedByDescending { it.value.size }
            .take(maxConcepts)
            .map { (_, hits) ->
                val displayName = hits.groupingBy { it.second }.eachCount().maxBy { it.value }.key
                val evidenceChunkIds = hits.map { it.first }.distinct().take(5)
                val firstChunk = chunks.first { it.id == hits.first().first }
                ExtractedConcept(
                    name = displayName,
                    frequency = hits.size,
                    summary = summarySnippet(firstChunk.text, displayName),
                    evidenceChunkIds = evidenceChunkIds,
                )
            }
    }

    private fun summarySnippet(text: String, term: String): String {
        val idx = text.indexOf(term, ignoreCase = true).takeIf { it >= 0 } ?: 0
        val start = (idx - 80).coerceAtLeast(0)
        val end = (idx + term.length + 80).coerceAtMost(text.length)
        return text.substring(start, end).trim()
    }
}
