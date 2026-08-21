package de.optadata.odil.learnwithme.platform

import java.util.UUID

/**
 * Wird von anderen Modulen implementiert (z.B. `content`) und vom `platform`-internen
 * [JobWorker][de.optadata.odil.learnwithme.platform.internal.job.JobWorker] per
 * Spring-DI eingesammelt — keine Compile-Abhängigkeit von `platform` auf `content` nötig,
 * die Umkehrung passiert über Dependency Injection (Strategy-Pattern).
 */
interface JobHandler {
    val type: JobType

    /** [payloadJson] ist genau das Objekt, das beim `enqueue` übergeben wurde (Jackson-serialisiert). */
    fun handle(jobId: UUID, payloadJson: String)
}
