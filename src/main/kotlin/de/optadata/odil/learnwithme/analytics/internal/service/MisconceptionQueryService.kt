package de.optadata.odil.learnwithme.analytics.internal.service

import de.optadata.odil.learnwithme.analytics.internal.domain.Misconception
import de.optadata.odil.learnwithme.analytics.internal.persistence.MisconceptionRepository
import org.springframework.stereotype.Service
import java.util.UUID

/** E3: wiederkehrende Fehlmuster eines Nutzers — `occurrences >= Schwelle` ist die "Flag" selbst. */
@Service
class MisconceptionQueryService(private val misconceptionRepository: MisconceptionRepository) {

    fun listForUser(workspaceId: UUID, userId: UUID): List<Misconception> =
        misconceptionRepository.findAllByWorkspaceIdAndUserIdOrderByLastSeenAtDesc(workspaceId, userId)
}
