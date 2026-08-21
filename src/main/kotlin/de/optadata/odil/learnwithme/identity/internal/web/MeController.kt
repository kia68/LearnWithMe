package de.optadata.odil.learnwithme.identity.internal.web

import de.optadata.odil.learnwithme.identity.internal.service.AccountService
import de.optadata.odil.learnwithme.identity.internal.web.dto.MeResponse
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** A5: eigenes Konto einsehen und vollständig löschen. */
@RestController
@RequestMapping("/api/v1/me")
class MeController(private val accountService: AccountService) {

    @GetMapping
    fun me(@AuthenticationPrincipal principal: TenantPrincipal): MeResponse = accountService.getMe(principal)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMe(@AuthenticationPrincipal principal: TenantPrincipal) {
        accountService.deleteAccount(principal)
    }
}
