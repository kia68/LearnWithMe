package de.optadata.odil.learnwithme.content.internal.dedup

import java.security.MessageDigest

/** SHA-256 über die Rohdatei — Grundlage für Dedup (B7, `sources.content_hash`). */
object ContentHasher {
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
