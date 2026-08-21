package de.optadata.odil.learnwithme.identity.internal.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Stellt Access-Token aus (A1: 15 Minuten Gültigkeit). Refresh-Token sind bewusst
 * opak und werden nicht hier, sondern in [de.optadata.odil.learnwithme.identity.internal.service.AuthService]
 * ausgestellt (serverseitige Widerrufbarkeit, siehe [de.optadata.odil.learnwithme.identity.internal.domain.RefreshToken]). */
@Component
class JwtIssuer(
    rsaPublicKey: RSAPublicKey,
    rsaPrivateKey: RSAPrivateKey,
) {
    private val encoder = NimbusJwtEncoder(
        ImmutableJWKSet(
            JWKSet(RSAKey.Builder(rsaPublicKey).privateKey(rsaPrivateKey).keyID("learnwithme-dev").build()),
        ),
    )

    fun issueAccessToken(userId: UUID, workspaceId: UUID, email: String): IssuedToken {
        val now = Instant.now()
        val expiresAt = now.plus(ACCESS_TOKEN_TTL)
        val claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(userId.toString())
            .audience(listOf(AUDIENCE))
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim("wsid", workspaceId.toString())
            .claim("email", email)
            .build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return IssuedToken(token, expiresAt)
    }

    companion object {
        const val ISSUER = "learnwithme"
        const val AUDIENCE = "learnwithme-api"
        val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)
    }
}

data class IssuedToken(val value: String, val expiresAt: Instant)
