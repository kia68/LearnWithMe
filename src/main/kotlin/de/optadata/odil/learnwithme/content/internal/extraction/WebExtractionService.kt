package de.optadata.odil.learnwithme.content.internal.extraction

import de.optadata.odil.learnwithme.shared.ApiException
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.io.IOException

private const val MIN_READABLE_CHARS = 200

data class WebExtraction(
    val plainText: String,
    val sections: List<BuiltSection>,
    val title: String?,
    val partial: Boolean,
    val partialReason: String?,
)

/**
 * B2/B3: Mozilla-Readability-Port (Readability4J), serverseitig für den URL-Import (B2)
 * genutzt und identisch auf das von der Extension gesendete DOM-Fragment angewendet (B3,
 * §14.3) — Client- und Serverpfad bleiben dadurch konsistent.
 *
 * Erkennt Paywall/JS-only-Seiten heuristisch über die Menge des von Readability
 * gefundenen Textes statt den Inhalt ungeprüft zu speichern (B2-Akzeptanzkriterium).
 */
@Component
class WebExtractionService {

    fun fetchAndExtract(url: String): WebExtraction {
        val html = try {
            Jsoup.connect(url)
                .userAgent("LearnWithMe/1.0 (+ingestion)")
                .timeout(15_000)
                .get()
                .outerHtml()
        } catch (ex: IOException) {
            throw ApiException(HttpStatus.BAD_GATEWAY, "Import fehlgeschlagen", "URL nicht erreichbar: ${ex.message}")
        }
        return extractFromHtml(html, url)
    }

    fun extractFromHtml(html: String, url: String?): WebExtraction {
        val article = Readability4J(url ?: "", html).parse()
        val contentHtml = article.content ?: html
        val result = SectionBuilder.build(contentHtml)
        val readableChars = article.textContent?.trim()?.length ?: result.plainText.length
        val partial = readableChars < MIN_READABLE_CHARS

        return WebExtraction(
            plainText = result.plainText,
            sections = result.sections,
            title = article.title,
            partial = partial,
            partialReason = if (partial) {
                "Zu wenig Text extrahiert — vermutlich Paywall oder JavaScript-gerendert."
            } else {
                null
            },
        )
    }
}
