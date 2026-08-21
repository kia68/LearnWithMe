package de.optadata.odil.learnwithme.platform.internal.job

import de.optadata.odil.learnwithme.platform.JobHandler
import de.optadata.odil.learnwithme.shared.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

private const val MAX_ATTEMPTS = 3

/**
 * Poller der DB-gestützten Job-Queue (ADR-012). Läuft dort, wo `learnwithme.jobs.enabled=true`
 * ist — Default `true` (lokale Entwicklung: ein Prozess macht beides), im `api`-Profil explizit
 * `false` (Produktion: API-Instanzen verarbeiten keine Jobs, siehe application.yml).
 *
 * [JobHandler]s werden per Spring-DI eingesammelt (Strategy-Pattern) — `platform` kennt die
 * konkreten Handler-Implementierungen (z.B. aus `content`) nicht, nur das Interface.
 */
@Component
@ConditionalOnProperty(prefix = "learnwithme.jobs", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class JobWorker(
    private val jobClaimer: JobClaimer,
    private val jobRepository: JobRepository,
    handlers: List<JobHandler>,
) {
    private val log = LoggerFactory.getLogger(JobWorker::class.java)
    private val handlersByType = handlers.associateBy { it.type }

    @Scheduled(fixedDelay = 1000)
    fun poll() {
        val job = jobClaimer.claimNext() ?: return
        val handler = handlersByType[job.type]
        if (handler == null) {
            fail(job, "Kein JobHandler für Typ ${job.type} registriert")
            return
        }
        try {
            TenantContext.set(job.workspaceId)
            handler.handle(job.id, job.payload)
            job.status = JobStatus.DONE
            job.finishedAt = Instant.now()
            jobRepository.save(job)
        } catch (ex: Exception) {
            log.warn("Job {} ({}) fehlgeschlagen, Versuch {}", job.id, job.type, job.attempts, ex)
            fail(job, ex.message ?: ex.javaClass.simpleName)
        } finally {
            TenantContext.clear()
        }
    }

    private fun fail(job: JobEntity, reason: String) {
        job.lastError = reason
        if (job.attempts < MAX_ATTEMPTS) {
            job.status = JobStatus.PENDING // erneuter Anlauf beim nächsten Poll
        } else {
            job.status = JobStatus.FAILED
            job.finishedAt = Instant.now()
        }
        jobRepository.save(job)
    }
}
