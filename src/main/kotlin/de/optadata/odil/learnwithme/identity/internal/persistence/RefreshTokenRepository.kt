package de.optadata.odil.learnwithme.identity.internal.persistence

import de.optadata.odil.learnwithme.identity.internal.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
}
