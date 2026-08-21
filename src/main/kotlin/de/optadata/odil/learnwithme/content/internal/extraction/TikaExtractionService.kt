package de.optadata.odil.learnwithme.content.internal.extraction

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import org.apache.tika.sax.ToXMLContentHandler
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

data class TikaExtraction(
    val plainText: String,
    val sections: List<BuiltSection>,
    val pageCount: Int?,
    val language: String?,
    val mimeType: String?,
)

/** B1/B4: einheitliche Extraktion für PDF/DOCX/EPUB/Plaintext über Apache Tika. Fragt XHTML
 * statt reinem Text ab, damit [SectionBuilder] `h1`..`h6` für die Strukturerkennung (B6) sieht. */
@Component
class TikaExtractionService {

    fun extract(bytes: ByteArray): TikaExtraction {
        val parser = AutoDetectParser()
        val metadata = Metadata()
        val xhtml = ToXMLContentHandler()
        val handler = BodyContentHandler(xhtml)
        ByteArrayInputStream(bytes).use { stream ->
            parser.parse(stream, handler, metadata, ParseContext())
        }
        val result = SectionBuilder.build(xhtml.toString())
        val pageCount = metadata.get("xmpTPg:NPages")?.toIntOrNull() ?: metadata.get("Page-Count")?.toIntOrNull()
        return TikaExtraction(
            plainText = result.plainText,
            sections = result.sections,
            pageCount = pageCount,
            language = metadata.get("language") ?: metadata.get("dc:language"),
            mimeType = metadata.get("Content-Type"),
        )
    }
}
