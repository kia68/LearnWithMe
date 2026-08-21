package de.optadata.odil.learnwithme.content.internal.persistence

import de.optadata.odil.learnwithme.content.internal.domain.Source
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SourceRepository : JpaRepository<Source, UUID> {
    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Source?

    fun findByWorkspaceIdAndContentHash(workspaceId: UUID, contentHash: ByteArray): Source?

    fun findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId: UUID, pageable: Pageable): Page<Source>
}
