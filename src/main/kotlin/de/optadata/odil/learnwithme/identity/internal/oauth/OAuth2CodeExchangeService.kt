package de.optadata.odil.learnwithme.identity.internal.oauth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.optadata.odil.learnwithme.identity.internal.domain.SsoProvider
import de.optadata.odil.learnwithme.shared.ApiException
import de.optadata.odil.learnwithme.shared.BadGatewayException
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Tauscht einen Authorization-Code (PKCE, vom Client selbst gegen Google/GitHub eingeholt —
 * §14.2) serverseitig gegen Provider-Tokens und anschließend gegen ein Nutzerprofil.
 *
 * Bewusst ohne Spring Securitys `oauth2Login`-Filterkette: Diese setzt eine serverseitig
 * gehaltene `OAuth2AuthorizationRequest` voraus, die hier nicht existiert, weil der
 * Autorisierungs-Redirect vom Client (Web/Extension) ausgeht, nicht vom Backend. Genutzt
 * wird lediglich [ClientRegistrationRepository] als Konfigurationsquelle (Client-ID/Secret/
 * Token-/UserInfo-URIs aus `spring.security.oauth2.client.registration.*`).
 */
@Component
class OAuth2CodeExchangeService(
    private val clientRegistrationRepository: ClientRegistrationRepository,
) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val objectMapper = ObjectMapper()

    fun exchange(provider: SsoProvider, code: String, codeVerifier: String, redirectUri: String): SsoIdentity {
        val registration = clientRegistrationRepository.findByRegistrationId(provider.registrationId)
            ?: throw ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SSO Provider nicht konfiguriert",
                "Provider ${provider.name} ist auf diesem Server nicht eingerichtet.",
            )

        val tokenForm = mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "client_id" to registration.clientId,
            "client_secret" to registration.clientSecret,
            "code_verifier" to codeVerifier,
        )
        val tokenJson = postForm(registration.providerDetails.tokenUri, tokenForm)
        val accessToken = tokenJson.get("access_token")?.asText()
            ?: throw BadGatewayException("Provider ${provider.name} lieferte keinen Access-Token zurück.")

        val userInfoUri = registration.providerDetails.userInfoEndpoint.uri
            ?: throw BadGatewayException("Provider ${provider.name} hat keinen UserInfo-Endpoint konfiguriert.")
        val userInfo = getJson(userInfoUri, accessToken)
        return when (provider) {
            SsoProvider.GOOGLE -> mapGoogleUserInfo(userInfo)
            SsoProvider.GITHUB -> mapGitHubUserInfo(userInfo, accessToken)
        }
    }

    private fun mapGoogleUserInfo(userInfo: JsonNode): SsoIdentity {
        val uid = userInfo.get("sub")?.asText()
            ?: throw BadGatewayException("Google lieferte keine Nutzer-ID (sub) zurück.")
        val email = userInfo.get("email")?.asText()
            ?: throw BadGatewayException("Google lieferte keine E-Mail-Adresse zurück.")
        return SsoIdentity(providerUid = uid, email = email, displayName = userInfo.get("name")?.asText())
    }

    private fun mapGitHubUserInfo(userInfo: JsonNode, accessToken: String): SsoIdentity {
        val uid = userInfo.get("id")?.asText()
            ?: throw BadGatewayException("GitHub lieferte keine Nutzer-ID zurück.")
        val displayName = userInfo.get("name")?.asText() ?: userInfo.get("login")?.asText()
        // GitHub liefert `email` im /user-Endpunkt nur, wenn sie öffentlich ist — sonst
        // muss die primäre verifizierte Adresse separat über /user/emails geholt werden.
        val email = userInfo.get("email")?.takeIf { !it.isNull }?.asText()
            ?: fetchPrimaryGitHubEmail(accessToken)
            ?: throw BadGatewayException(
                "GitHub-Konto hat keine öffentliche oder verifizierte primäre E-Mail-Adresse.",
            )
        return SsoIdentity(providerUid = uid, email = email, displayName = displayName)
    }

    private fun fetchPrimaryGitHubEmail(accessToken: String): String? {
        val request = HttpRequest.newBuilder(URI.create("https://api.github.com/user/emails"))
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return null
        val emails = objectMapper.readTree(response.body())
        return emails.firstOrNull { it.get("primary")?.asBoolean() == true && it.get("verified")?.asBoolean() == true }
            ?.get("email")?.asText()
    }

    private fun postForm(uri: String, form: Map<String, String>): JsonNode {
        val body = form.entries.joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, Charsets.UTF_8)}" }
        val request = HttpRequest.newBuilder(URI.create(uri))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json") // GitHub antwortet sonst form-urlencoded
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw BadGatewayException("Token-Austausch fehlgeschlagen (HTTP ${response.statusCode()}).")
        }
        return objectMapper.readTree(response.body())
    }

    private fun getJson(uri: String, accessToken: String): JsonNode {
        val request = HttpRequest.newBuilder(URI.create(uri))
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw BadGatewayException("Abruf des Nutzerprofils fehlgeschlagen (HTTP ${response.statusCode()}).")
        }
        return objectMapper.readTree(response.body())
    }
}
