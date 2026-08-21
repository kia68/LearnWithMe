package de.optadata.odil.learnwithme.content.internal.chunking

/** Ein Chunk mit exakten Zeichen-Offsets im Volltext (P1: exaktes Zitat, unabhängig von der
 * Chunk-Zerlegung — die Quelle bleibt über `charFrom`/`charTo` immer nachvollziehbar). */
data class ChunkSpan(val ordinal: Int, val text: String, val charFrom: Int, val charTo: Int, val tokenCount: Int)

/**
 * Zerlegt Volltext in Chunks nahe [targetTokens] mit [overlapTokens] Überlappung zwischen
 * aufeinanderfolgenden Chunks (B1/B4-Voraussetzung für Chunking+Indexing). Schneidet primär an
 * Absatzgrenzen (Leerzeilen); ein einzelner Absatz, der allein schon deutlich über dem
 * Zielbudget liegt (z.B. eine OCR-freie PDF-Seite ohne erkennbare Absätze), wird zusätzlich an
 * Wortgrenzen hart geschnitten, damit kein Chunk unbegrenzt wächst.
 */
class Chunker(private val targetTokens: Int, private val overlapTokens: Int) {

    fun chunk(text: String): List<ChunkSpan> {
        val maxLen = (targetTokens * TokenEstimator.CHARS_PER_TOKEN).toInt().coerceAtLeast(1)
        val overlapLen = (overlapTokens * TokenEstimator.CHARS_PER_TOKEN).toInt().coerceAtLeast(0)
        val segments = segment(text, maxLen)
        if (segments.isEmpty()) return emptyList()

        val spans = mutableListOf<ChunkSpan>()
        var i = 0
        while (i < segments.size) {
            val start = segments[i].first
            var end = segments[i].last
            var j = i + 1
            while (j < segments.size && (end - start) < maxLen) {
                end = segments[j].last
                j++
            }
            val chunkText = text.substring(start, end)
            spans += ChunkSpan(spans.size, chunkText, start, end, TokenEstimator.estimate(chunkText))

            var next = j
            var k = j - 1
            while (k > i && (end - segments[k].first) < overlapLen) k--
            if (k > i) next = k
            i = maxOf(next, i + 1) // Fortschritt garantieren
        }
        return spans
    }

    /** Absätze (Leerzeile-getrennt); überlange Absätze werden an Wortgrenzen nachgeschnitten. */
    private fun segment(text: String, maxLen: Int): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var cursor = 0
        for (paragraph in Regex("\\n\\s*\\n").split(text)) {
            if (paragraph.isBlank()) {
                cursor += paragraph.length + 2
                continue
            }
            val start = text.indexOf(paragraph, cursor).let { if (it < 0) cursor else it }
            val end = (start + paragraph.length).coerceAtMost(text.length)
            cursor = end
            if (paragraph.length <= maxLen * 3) {
                ranges += start..end
            } else {
                ranges += hardSplit(text, start, end, maxLen)
            }
        }
        return ranges
    }

    private fun hardSplit(text: String, from: Int, to: Int, maxLen: Int): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var pos = from
        while (pos < to) {
            var end = minOf(pos + maxLen, to)
            if (end < to) {
                val ws = text.lastIndexOf(' ', end)
                if (ws > pos) end = ws
            }
            result += pos..end
            pos = end
            while (pos < to && text[pos] == ' ') pos++
        }
        return result
    }
}
