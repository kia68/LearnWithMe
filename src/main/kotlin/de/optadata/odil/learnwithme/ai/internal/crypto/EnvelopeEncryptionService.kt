package de.optadata.odil.learnwithme.ai.internal.crypto

import org.springframework.stereotype.Component

/** Envelope-Verschlüsselung eines BYOK-API-Keys (ADR-009): ein zufälliges DEK pro
 * Credential verschlüsselt den Klartext, das DEK selbst wird vom [KeyEncryptionService]
 * (KEK) eingewickelt. Der Klartext existiert nur für die Dauer eines Aufrufs im Speicher. */
@Component
class EnvelopeEncryptionService(private val keyEncryptionService: KeyEncryptionService) {

    fun encrypt(plaintext: String): Envelope {
        val dekBytes = AesGcm.randomKeyBytes()
        val dek = AesGcm.keyFrom(dekBytes)
        val (ciphertext, iv) = AesGcm.encrypt(dek, plaintext.toByteArray(Charsets.UTF_8))
        val wrappedDek = keyEncryptionService.wrap(dekBytes)
        return Envelope(ciphertext = ciphertext, wrappedDek = wrappedDek, nonce = iv)
    }

    fun decrypt(envelope: Envelope): String {
        val dekBytes = keyEncryptionService.unwrap(envelope.wrappedDek)
        val dek = AesGcm.keyFrom(dekBytes)
        val plaintext = AesGcm.decrypt(dek, envelope.ciphertext, envelope.nonce)
        return String(plaintext, Charsets.UTF_8)
    }
}

data class Envelope(val ciphertext: ByteArray, val wrappedDek: ByteArray, val nonce: ByteArray)
