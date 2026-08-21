package de.optadata.odil.learnwithme.platform

import java.util.UUID

/** Öffentlicher Port für den Objektspeicher (§6.2: MinIO lokal / Hetzner-S3 prod). */
interface StorageService {
    /** Speichert [bytes] innerhalb von [workspaceId] und liefert den Storage-Key zurück. */
    fun store(workspaceId: UUID, filename: String, bytes: ByteArray): String

    fun load(key: String): ByteArray

    fun delete(key: String)
}
