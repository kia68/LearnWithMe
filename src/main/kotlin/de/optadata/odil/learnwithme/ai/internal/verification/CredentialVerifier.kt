package de.optadata.odil.learnwithme.ai.internal.verification

import de.optadata.odil.learnwithme.ai.internal.domain.AiProvider
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class VerificationResult(val success: Boolean, val message: String)

/**
 * A3 „Testcall → Status". Ruft je Provider einen leichtgewichtigen, lesenden Endpunkt
 * auf (Modell-/Tag-Liste) statt eine echte Generierung anzustoßen — kostet beim
 * Provider nichts oder nahezu nichts und bestätigt trotzdem, dass der Key gültig ist.
 * Bewusst mit `java.net.http.HttpClient` statt Spring AI (ADR-004: Spring AI ist die
 * Chat-/Embedding-Abstraktion, kein genereller Provider-HTTP-Client).
 */
@Component
class CredentialVerifier {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun verify(provider: AiProvider, apiKey: String, baseUrl: String?): VerificationResult {
        val request = try {
            buildRequest(provider, apiKey, baseUrl)
        } catch (ex: IllegalArgumentException) {
            return VerificationResult(false, ex.message ?: "Ungültige Konfiguration")
        }
        return try {
            val response = http.send(request, HttpResponse.BodyHandlers.discarding())
            when (response.statusCode()) {
                in 200..299 -> VerificationResult(true, "Verifiziert")
                401, 403 -> VerificationResult(false, "Provider lehnt den Key ab (HTTP ${response.statusCode()})")
                else -> VerificationResult(false, "Provider antwortete mit HTTP ${response.statusCode()}")
            }
        } catch (ex: Exception) {
            VerificationResult(false, "Provider nicht erreichbar: ${ex.message}")
        }
    }

    private fun buildRequest(provider: AiProvider, apiKey: String, baseUrl: String?): HttpRequest {
        val builder = when (provider) {
            AiProvider.OPENAI -> HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/models"))
                .header("Authorization", "Bearer $apiKey")

            AiProvider.ANTHROPIC -> HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/models"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")

            AiProvider.GOOGLE -> HttpRequest.newBuilder(
                URI.create("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"),
            )

            AiProvider.OPENROUTER -> HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/models"))
                .header("Authorization", "Bearer $apiKey")

            AiProvider.AZURE -> {
                val url = requireNotNull(baseUrl?.trimEnd('/')) { "Azure OpenAI benötigt eine Base-URL" }
                HttpRequest.newBuilder(URI.create("$url/openai/models?api-version=2024-02-01"))
                    .header("api-key", apiKey)
            }

            AiProvider.OLLAMA -> {
                val url = (baseUrl?.trimEnd('/')).takeUnless { it.isNullOrBlank() } ?: "http://localhost:11434"
                HttpRequest.newBuilder(URI.create("$url/api/tags"))
            }
        }
        return builder.header("Accept", "application/json").timeout(Duration.ofSeconds(5)).GET().build()
    }
}
