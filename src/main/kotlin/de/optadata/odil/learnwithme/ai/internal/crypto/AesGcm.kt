package de.optadata.odil.learnwithme.ai.internal.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM Hilfsfunktionen für die Envelope-Verschlüsselung (ADR-009). */
internal object AesGcm {
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private val secureRandom = SecureRandom()

    fun randomKeyBytes(): ByteArray = ByteArray(KEY_BYTES).also { secureRandom.nextBytes(it) }

    fun keyFrom(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

    /** @return ciphertext (inkl. GCM-Tag) und der dafür verwendete IV. */
    fun encrypt(key: SecretKey, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(plaintext) to iv
    }

    fun decrypt(key: SecretKey, ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /** Für Werte mit nur einer Spalte (KEK-Wrap): IV wird dem Ciphertext vorangestellt. */
    fun encryptWithEmbeddedIv(key: SecretKey, plaintext: ByteArray): ByteArray {
        val (ciphertext, iv) = encrypt(key, plaintext)
        return iv + ciphertext
    }

    fun decryptWithEmbeddedIv(key: SecretKey, wrapped: ByteArray): ByteArray {
        require(wrapped.size > IV_BYTES) { "Wrapped value zu kurz für IV+Ciphertext" }
        val iv = wrapped.copyOfRange(0, IV_BYTES)
        val ciphertext = wrapped.copyOfRange(IV_BYTES, wrapped.size)
        return decrypt(key, ciphertext, iv)
    }
}
