package de.optadata.odil.learnwithme.ai.internal.web

import de.optadata.odil.learnwithme.ai.internal.domain.AiProvider
import de.optadata.odil.learnwithme.ai.internal.service.AiCredentialService
import de.optadata.odil.learnwithme.ai.internal.web.dto.CreateCredentialRequest
import de.optadata.odil.learnwithme.ai.internal.web.dto.CredentialResponse
import de.optadata.odil.learnwithme.ai.internal.web.dto.ProviderInfo
import de.optadata.odil.learnwithme.ai.internal.web.dto.VerifyCredentialResponse
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** A3: BYOK — eigenen AI-Account/API-Key hinterlegen, verifizieren, löschen. */
@RestController
@RequestMapping("/api/v1/ai")
class AiCredentialController(private val credentialService: AiCredentialService) {

    @GetMapping("/providers")
    fun providers(): List<ProviderInfo> =
        AiProvider.entries.map { ProviderInfo(it, it.displayName, it.requiresBaseUrl) }

    @GetMapping("/credentials")
    fun list(@AuthenticationPrincipal principal: TenantPrincipal): List<CredentialResponse> =
        credentialService.list(principal.workspaceId)

    @PostMapping("/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @Valid @RequestBody request: CreateCredentialRequest,
    ): CredentialResponse = credentialService.create(principal.workspaceId, request)

    @PostMapping("/credentials/{id}/verify")
    fun verify(
        @AuthenticationPrincipal principal: TenantPrincipal,
        @PathVariable id: UUID,
    ): VerifyCredentialResponse = credentialService.verify(principal.workspaceId, id)

    @DeleteMapping("/credentials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable id: UUID) {
        credentialService.delete(principal.workspaceId, id)
    }
}
