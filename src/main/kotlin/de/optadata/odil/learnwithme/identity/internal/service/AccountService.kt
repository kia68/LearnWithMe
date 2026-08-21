package de.optadata.odil.learnwithme.identity.internal.service

import de.optadata.odil.learnwithme.identity.internal.domain.AccountDeletionRecord
import de.optadata.odil.learnwithme.identity.internal.persistence.AccountDeletionRepository
import de.optadata.odil.learnwithme.identity.internal.persistence.UserRepository
import de.optadata.odil.learnwithme.identity.internal.web.dto.MeResponse
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import de.optadata.odil.learnwithme.shared.web.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Kontoverwaltung (A5). Löschung ist ein sofortiges Hard-Delete: `users` trägt
 * `ON DELETE CASCADE` auf `workspaces`, `refresh_tokens`, `linked_identities` und
 * (über `workspaces`) `ai_credentials` — damit ist die 24h-Frist der DSGVO-Löschung
 * trivial erfüllt. Sobald das `content`-Modul existiert (Epic B), muss die Löschung um
 * das Aufräumen des Objektspeichers (S3) erweitert werden.
 */
@Service
class AccountService(
    private val userRepository: UserRepository,
    private val accountDeletionRepository: AccountDeletionRepository,
) {

    fun getMe(principal: TenantPrincipal): MeResponse {
        val user = userRepository.findById(principal.userId).orElseThrow {
            NotFoundException("Nutzer ${principal.userId} nicht gefunden")
        }
        return MeResponse(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            locale = user.locale,
            plan = user.plan.name,
            workspaceId = principal.workspaceId,
            createdAt = user.createdAt,
        )
    }

    @Transactional
    fun deleteAccount(principal: TenantPrincipal) {
        val user = userRepository.findById(principal.userId).orElseThrow {
            NotFoundException("Nutzer ${principal.userId} nicht gefunden")
        }
        accountDeletionRepository.save(AccountDeletionRecord(userId = user.id, email = user.email))
        userRepository.delete(user)
    }
}
