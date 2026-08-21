package de.optadata.odil.learnwithme.ai.internal.crypto

/**
 * Port zum Key Encryption Key (KEK) eines externen Key-Managements (ADR-009). Wickelt
 * pro Credential zufällig erzeugte Data Encryption Keys (DEK) ein/aus. In Produktion
 * MUSS eine Implementierung gegen ein echtes KMS/Vault treten; [LocalDevKeyEncryptionService]
 * ist ausschließlich für lokale Entwicklung gedacht.
 */
interface KeyEncryptionService {
    fun wrap(dek: ByteArray): ByteArray
    fun unwrap(wrappedDek: ByteArray): ByteArray
}
