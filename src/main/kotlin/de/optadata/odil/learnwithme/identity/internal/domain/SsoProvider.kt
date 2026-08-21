package de.optadata.odil.learnwithme.identity.internal.domain

/** Unterstützte SSO-Identitätsprovider (A1). [registrationId] muss mit
 * `spring.security.oauth2.client.registration.<id>` übereinstimmen. */
enum class SsoProvider(val registrationId: String) {
    GOOGLE("google"),
    GITHUB("github"),
}
