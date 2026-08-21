package de.optadata.odil.learnwithme.platform.internal.job

import com.fasterxml.jackson.databind.ObjectMapper
import de.optadata.odil.learnwithme.platform.JobQueue
import de.optadata.odil.learnwithme.platform.JobType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class JobQueueImpl(private val jobRepository: JobRepository) : JobQueue {

    private val objectMapper = ObjectMapper()

    @Transactional
    override fun enqueue(type: JobType, jobKey: String, workspaceId: UUID, payload: Map<String, Any?>): UUID {
        jobRepository.findByJobKey(jobKey)?.let { return it.id }
        val job = jobRepository.save(
            JobEntity(jobKey = jobKey, type = type, workspaceId = workspaceId, payload = objectMapper.writeValueAsString(payload)),
        )
        return job.id
    }
}
