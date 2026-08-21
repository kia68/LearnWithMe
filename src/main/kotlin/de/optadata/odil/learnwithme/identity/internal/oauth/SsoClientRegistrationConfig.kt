package de.optadata.odil.learnwithme.identity.internal.oauth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

/**
 * Baut die [ClientRegistrationRepository] manuell statt über Spring Boots
 * `spring.security.oauth2.client.registration.*`-Autokonfiguration (die bei fehlenden
 * Properties entweder gar keine Bean erzeugt oder bei leerem `client-id` beim Start
 * abstürzt). So startet die App auch ohne konfigurierte SSO-Secrets — Provider ohne
 * Credentials werden weggelassen; [OAuth2CodeExchangeService] meldet das dann sauber
 * als „Provider nicht konfiguriert" (503) statt die App unstartbar zu machen.
 *
 * Die Endpunkt-URLs sind bewusst fest hinterlegt statt über das (in Spring Security 7
 * entfernte) `CommonOAuth2Provider` bezogen — Google/GitHubs OAuth2-Endpunkte sind
 * seit Jahren stabil und öffentlich dokumentiert.
 */
@Configuration
class SsoClientRegistrationConfig(
    @Value("\${learnwithme.sso.google.client-id:}") private val googleClientId: String,
    @Value("\${learnwithme.sso.google.client-secret:}") private val googleClientSecret: String,
    @Value("\${learnwithme.sso.github.client-id:}") private val githubClientId: String,
    @Value("\${learnwithme.sso.github.client-secret:}") private val githubClientSecret: String,
) {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        val registrations = listOfNotNull(googleRegistration(), githubRegistration())
            .associateBy { it.registrationId }
        return ClientRegistrationRepository { registrationId -> registrations[registrationId] }
    }

    private fun googleRegistration(): ClientRegistration? {
        if (googleClientId.isBlank() || googleClientSecret.isBlank()) return null
        return ClientRegistration.withRegistrationId("google")
            .clientId(googleClientId)
            .clientSecret(googleClientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
            .userNameAttributeName("sub")
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .clientName("Google")
            .build()
    }

    private fun githubRegistration(): ClientRegistration? {
        if (githubClientId.isBlank() || githubClientSecret.isBlank()) return null
        return ClientRegistration.withRegistrationId("github")
            .clientId(githubClientId)
            .clientSecret(githubClientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("read:user", "user:email")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .userInfoUri("https://api.github.com/user")
            .userNameAttributeName("id")
            .clientName("GitHub")
            .build()
    }
}
