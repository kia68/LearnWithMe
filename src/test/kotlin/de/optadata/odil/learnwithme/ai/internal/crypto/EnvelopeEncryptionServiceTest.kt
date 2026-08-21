package de.optadata.odil.learnwithme.ai.internal.crypto

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class EnvelopeEncryptionServiceTest {

    private val service = EnvelopeEncryptionService(LocalDevKeyEncryptionService(kekBase64 = ""))

    @Test
    fun `round-trips plaintext through encrypt and decrypt`() {
        val plaintext = "sk-test-1234567890abcdef"

        val envelope = service.encrypt(plaintext)
        val decrypted = service.decrypt(envelope)

        decrypted shouldBe plaintext
    }

    @Test
    fun `ciphertext never equals the plaintext bytes`() {
        val plaintext = "sk-another-secret-key"

        val envelope = service.encrypt(plaintext)

        String(envelope.ciphertext, Charsets.ISO_8859_1) shouldNotBe plaintext
    }

    @Test
    fun `each encryption uses a fresh nonce and wrapped DEK`() {
        val plaintext = "sk-same-key-twice"

        val first = service.encrypt(plaintext)
        val second = service.encrypt(plaintext)

        first.nonce shouldNotBe second.nonce
        first.ciphertext shouldNotBe second.ciphertext
    }

    @Test
    fun `decrypting with a different KEK fails`() {
        val envelope = service.encrypt("sk-cross-instance")
        val otherInstance = EnvelopeEncryptionService(LocalDevKeyEncryptionService(kekBase64 = ""))

        try {
            otherInstance.decrypt(envelope)
            throw AssertionError("Erwartete Exception blieb aus — fremder KEK durfte nicht entschlüsseln können")
        } catch (ex: Exception) {
            // erwartet: GCM-Tag-Prüfung schlägt mit fremdem KEK fehl
        }
    }
}
