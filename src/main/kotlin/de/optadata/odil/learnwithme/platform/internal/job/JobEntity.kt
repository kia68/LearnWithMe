package de.optadata.odil.learnwithme.platform.internal.job

import de.optadata.odil.learnwithme.platform.JobType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Hintergrund-Job (ADR-012). `payload` ist Jackson-JSON, siehe [JobRepository]. */
@Entity
@Table(name = "jobs")
class JobEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "job_key", nullable = false, unique = true)
    val jobKey: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: JobType,

    @Column(nullable = false)
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: JobStatus = JobStatus.PENDING,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_error")
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,
)
