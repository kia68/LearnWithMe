package de.optadata.odil.learnwithme.content.internal.service

import de.optadata.odil.learnwithme.content.internal.domain.SourceKind
import de.optadata.odil.learnwithme.shared.ApiException
import org.springframework.http.HttpStatus

/** B1/B4: leitet [SourceKind] aus Dateiendung/Content-Type ab. */
object SourceKindDetector {
    private val markdownPlaintextExtensions = setOf("md", "markdown", "txt")

    fun detect(filename: String?, contentType: String?): SourceKind {
        val extension = filename?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        return when {
            extension == "pdf" || contentType == "application/pdf" -> SourceKind.PDF
            extension == "docx" -> SourceKind.DOCX
            extension == "epub" -> SourceKind.EPUB
            extension in markdownPlaintextExtensions -> SourceKind.TEXT
            contentType?.startsWith("text/") == true -> SourceKind.TEXT
            else -> throw ApiException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Format nicht unterstützt",
                "Dateiformat wird nicht unterstützt (Datei: ${filename ?: "?"}, Content-Type: ${contentType ?: "?"}).",
            )
        }
    }
}
