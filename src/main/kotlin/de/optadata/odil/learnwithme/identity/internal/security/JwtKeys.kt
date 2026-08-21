package de.optadata.odil.learnwithme.identity.internal.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * RSA-Schlüsselpaar zum Signieren/Validieren der selbst ausgestellten Access-Token
 * (ADR: self-issued JWT statt externem Authorization Server).
 *
 * Für den aktuellen Walking-Skeleton wird das Paar beim Prozessstart generiert —
 * das invalidiert bestehende Access-Token bei jedem Neustart. Für den Produktivbetrieb
 * MUSS ein persistentes, rotierbares Schlüsselpaar aus einem KMS/Vault treten
 * (gleiche Anforderung wie ADR-009 für den BYOK-KEK).
 */
@Configuration
class JwtKeys {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    @Bean
    fun rsaPublicKey(): RSAPublicKey = keyPair.public as RSAPublicKey

    @Bean
    fun rsaPrivateKey(): RSAPrivateKey = keyPair.private as RSAPrivateKey
}
