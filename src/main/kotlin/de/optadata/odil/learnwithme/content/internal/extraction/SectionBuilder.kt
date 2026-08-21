package de.optadata.odil.learnwithme.content.internal.extraction

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Eine erkannte Überschrift (B6). `charFrom` ist der Offset ihres Titels im Volltext —
 * die Elternzuordnung (Baum) baut die Persistenzschicht per Level-Stack aus der flachen Liste. */
data class BuiltSection(val ordinal: Int, val level: Int, val title: String, val charFrom: Int)

data class ExtractionResult(val plainText: String, val sections: List<BuiltSection>)

/**
 * Baut Volltext + Dokumentstruktur (B6) aus (X)HTML: `h1`..`h6` werden zu Sections, alle
 * anderen Textknoten fließen fortlaufend in den Volltext. Wird sowohl auf Tika-XHTML
 * (PDF/DOCX/EPUB, B4) als auch auf Readability-HTML (URL/Extension-Snippet, B2/B3)
 * angewendet, damit Client- und Serverpfad strukturell gleich behandelt werden (§14.3).
 */
object SectionBuilder {

    private val headingLevels = mapOf("h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6)
    private val blockTags = setOf(
        "p", "div", "li", "ul", "ol", "section", "article", "blockquote",
        "tr", "table", "br", "h1", "h2", "h3", "h4", "h5", "h6",
    )

    fun build(html: String): ExtractionResult {
        val body = Jsoup.parse(html).body()
        val text = StringBuilder()
        val sections = mutableListOf<BuiltSection>()

        fun ensureBlankLine() {
            if (text.isEmpty()) return
            if (!text.endsWith("\n")) text.append('\n')
            if (!text.endsWith("\n\n")) text.append('\n')
        }

        fun walk(node: Node) {
            when (node) {
                is TextNode -> {
                    val t = node.text().trim()
                    if (t.isNotEmpty()) {
                        if (text.isNotEmpty() && !text.endsWith("\n") && !text.endsWith(" ")) text.append(' ')
                        text.append(t)
                    }
                }
                is Element -> {
                    val level = headingLevels[node.tagName().lowercase()]
                    if (level != null) {
                        ensureBlankLine()
                        val title = node.text().trim().ifBlank { "Abschnitt ${sections.size + 1}" }
                        sections += BuiltSection(sections.size, level, title, text.length)
                        text.append(title)
                        ensureBlankLine()
                    } else {
                        node.childNodes().forEach(::walk)
                        if (node.tagName().lowercase() in blockTags) ensureBlankLine()
                    }
                }
            }
        }
        body.childNodes().forEach(::walk)
        return ExtractionResult(text.toString().trim(), sections)
    }
}
