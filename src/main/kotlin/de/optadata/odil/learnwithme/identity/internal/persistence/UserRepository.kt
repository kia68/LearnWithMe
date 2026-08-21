package de.optadata.odil.learnwithme.identity.internal.persistence

import de.optadata.odil.learnwithme.identity.internal.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmailIgnoreCase(email: String): User?
}
