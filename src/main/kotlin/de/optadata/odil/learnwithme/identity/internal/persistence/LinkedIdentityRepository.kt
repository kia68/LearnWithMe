package de.optadata.odil.learnwithme.identity.internal.persistence

import de.optadata.odil.learnwithme.identity.internal.domain.LinkedIdentity
import de.optadata.odil.learnwithme.identity.internal.domain.SsoProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LinkedIdentityRepository : JpaRepository<LinkedIdentity, UUID> {
    fun findByProviderAndProviderUid(provider: SsoProvider, providerUid: String): LinkedIdentity?
}
