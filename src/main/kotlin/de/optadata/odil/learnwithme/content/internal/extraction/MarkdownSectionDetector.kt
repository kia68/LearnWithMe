package de.optadata.odil.learnwithme.content.internal.extraction

/** B4/B6-Fallback: Tika liefert für Markdown/Plaintext keine HTML-Überschriften, ATX-Headings
 * (`#`..`######`) lassen sich aber direkt im extrahierten Text erkennen. */
object MarkdownSectionDetector {
    private val headingLine = Regex("(?m)^(#{1,6})\\s+(.+?)\\s*$")

    fun detect(text: String): List<BuiltSection> =
        headingLine.findAll(text).mapIndexed { index, match ->
            BuiltSection(
                ordinal = index,
                level = match.groupValues[1].length,
                title = match.groupValues[2].trim(),
                charFrom = match.range.first,
            )
        }.toList()
}
