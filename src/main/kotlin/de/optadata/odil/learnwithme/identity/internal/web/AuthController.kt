package de.optadata.odil.learnwithme.identity.internal.web

import de.optadata.odil.learnwithme.identity.internal.service.AuthService
import de.optadata.odil.learnwithme.identity.internal.web.dto.LoginRequest
import de.optadata.odil.learnwithme.identity.internal.web.dto.LogoutRequest
import de.optadata.odil.learnwithme.identity.internal.web.dto.RefreshRequest
import de.optadata.odil.learnwithme.identity.internal.web.dto.RegisterRequest
import de.optadata.odil.learnwithme.identity.internal.web.dto.SsoTokenRequest
import de.optadata.odil.learnwithme.identity.internal.web.dto.TokenResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** A1: Registrierung, Login und SSO-Anmeldung; Token-Lebenszyklus (Refresh/Logout). */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): TokenResponse =
        authService.register(request.email, request.password, request.displayName)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse =
        authService.login(request.email, request.password)

    /** OAuth2/OIDC Code→Token (PKCE) für Google/GitHub-SSO (A1, A2). */
    @PostMapping("/token")
    fun ssoToken(@Valid @RequestBody request: SsoTokenRequest): TokenResponse =
        authService.ssoLogin(request.provider, request.code, request.codeVerifier, request.redirectUri)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): TokenResponse =
        authService.refresh(request.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: LogoutRequest): ResponseEntity<Void> {
        authService.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
