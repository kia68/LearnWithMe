package de.optadata.odil.learnwithme.ai.internal.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey

/**
 * Dev-KEK-Pfad (ADR-009, Konsequenz „lokale Entwicklung braucht einen Dev-KEK-Pfad").
 * Der KEK kommt entweder aus `learnwithme.security.dev-kek-base64` (32 Byte, Base64 —
 * z.B. gesetzt in `.env.local`) oder wird beim Start zufällig erzeugt.
 *
 * ⚠ Ohne gesetzten KEK übersteht kein gespeichertes Credential einen Neustart — das ist für
 * lokale Entwicklung akzeptabel, aber NICHT produktionstauglich. Für Produktion muss diese
 * Implementierung durch eine echte KMS/Vault-Anbindung ersetzt werden (Cloud-KMS,
 * HashiCorp Vault, Infisical — siehe ADR-009).
 */
@Component
class LocalDevKeyEncryptionService(
    @Value("\${learnwithme.security.dev-kek-base64:}") kekBase64: String,
) : KeyEncryptionService {

    private val kek: SecretKey = if (kekBase64.isNotBlank()) {
        val bytes = Base64.getDecoder().decode(kekBase64)
        require(bytes.size == 32) { "learnwithme.security.dev-kek-base64 muss 256 Bit (32 Byte) sein" }
        AesGcm.keyFrom(bytes)
    } else {
        AesGcm.keyFrom(ByteArray(32).also { SecureRandom().nextBytes(it) })
    }

    override fun wrap(dek: ByteArray): ByteArray = AesGcm.encryptWithEmbeddedIv(kek, dek)

    override fun unwrap(wrappedDek: ByteArray): ByteArray = AesGcm.decryptWithEmbeddedIv(kek, wrappedDek)
}
