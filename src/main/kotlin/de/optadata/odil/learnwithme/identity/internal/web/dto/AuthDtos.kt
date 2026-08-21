package de.optadata.odil.learnwithme.identity.internal.web.dto

import de.optadata.odil.learnwithme.identity.internal.domain.SsoProvider
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    @field:Email @field:NotBlank val email: String,
    @field:Size(min = 8, max = 100, message = "Passwort muss mindestens 8 Zeichen haben") val password: String,
    val displayName: String? = null,
)

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class SsoTokenRequest(
    val provider: SsoProvider,
    @field:NotBlank val code: String,
    @field:NotBlank val codeVerifier: String,
    @field:NotBlank val redirectUri: String,
)

data class RefreshRequest(@field:NotBlank val refreshToken: String)

data class LogoutRequest(@field:NotBlank val refreshToken: String)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)

data class MeResponse(
    val id: UUID,
    val email: String,
    val displayName: String?,
    val locale: String,
    val plan: String,
    val workspaceId: UUID,
    val createdAt: Instant,
)
