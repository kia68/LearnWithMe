package de.optadata.odil.learnwithme.identity.internal.oauth

/** Ergebnis des Code→Token-Austauschs bei einem SSO-Provider (A1). */
data class SsoIdentity(
    val providerUid: String,
    val email: String,
    val displayName: String?,
)
