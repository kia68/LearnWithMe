package de.optadata.odil.learnwithme.platform

import java.util.UUID

/** Grobes, modulübergreifend sicheres Abbild von `platform.internal.job.JobStatus` (RUNNING wird
 * wie PENDING behandelt — für Aufrufer außerhalb von `platform` nicht unterscheidbar relevant). */
enum class JobOutcome { PENDING, DONE, FAILED }

/** [lastError] ist nur bei [JobOutcome.FAILED] gesetzt (`platform.internal.job.JobEntity.lastError`
 * nach dem letzten erschöpften Versuch, siehe `JobWorker.MAX_ATTEMPTS`). */
data class JobStatusView(val outcome: JobOutcome, val lastError: String?)

/**
 * Öffentlicher Port des `platform`-Moduls zum Einreihen von Hintergrund-Jobs (ADR-012).
 * Idempotent über [jobKey]: existiert bereits ein Job mit diesem Schlüssel, liefert
 * `enqueue` dessen ID zurück, statt einen weiteren Job anzulegen (z.B. Doppelklick auf Upload).
 */
interface JobQueue {
    fun enqueue(type: JobType, jobKey: String, workspaceId: UUID, payload: Map<String, Any?>): UUID

    /** Für Poll-Endpunkte, die nur einen `jobKey`-Präfix kennen (z.B. `assessment`s
     * `GRADE_FREE_TEXT`-Grading, dessen `jobKey` zusätzlich einen Enqueue-Zeitstempel trägt).
     * `null`, wenn kein passender Job existiert. */
    fun statusByKeyPrefix(workspaceId: UUID, jobKeyPrefix: String): JobStatusView?
}
