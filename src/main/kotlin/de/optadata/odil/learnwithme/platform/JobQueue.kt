package de.optadata.odil.learnwithme.platform

import java.util.UUID

/**
 * Öffentlicher Port des `platform`-Moduls zum Einreihen von Hintergrund-Jobs (ADR-012).
 * Idempotent über [jobKey]: existiert bereits ein Job mit diesem Schlüssel, liefert
 * `enqueue` dessen ID zurück, statt einen weiteren Job anzulegen (z.B. Doppelklick auf Upload).
 */
interface JobQueue {
    fun enqueue(type: JobType, jobKey: String, payload: Map<String, Any?>): UUID
}
