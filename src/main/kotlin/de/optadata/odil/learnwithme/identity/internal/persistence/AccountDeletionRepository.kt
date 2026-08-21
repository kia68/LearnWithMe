package de.optadata.odil.learnwithme.identity.internal.persistence

import de.optadata.odil.learnwithme.identity.internal.domain.AccountDeletionRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountDeletionRepository : JpaRepository<AccountDeletionRecord, UUID>
