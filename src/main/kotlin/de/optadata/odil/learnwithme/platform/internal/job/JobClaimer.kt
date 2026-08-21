package de.optadata.odil.learnwithme.platform.internal.job

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Eigene Transaktion pro Claim-Versuch, damit der `FOR UPDATE`-Lock aus
 * [JobRepository.lockNextPending] nur so lange gehalten wird wie nötig. */
@Component
class JobClaimer(private val jobRepository: JobRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claimNext(): JobEntity? {
        val job = jobRepository.lockNextPending() ?: return null
        job.status = JobStatus.RUNNING
        job.attempts += 1
        job.startedAt = Instant.now()
        return jobRepository.save(job)
    }
}
