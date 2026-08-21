package de.optadata.odil.learnwithme.identity.internal.service

import de.optadata.odil.learnwithme.identity.internal.domain.LinkedIdentity
import de.optadata.odil.learnwithme.identity.internal.domain.RefreshToken
import de.optadata.odil.learnwithme.identity.internal.domain.SsoProvider
import de.optadata.odil.learnwithme.identity.internal.domain.User
import de.optadata.odil.learnwithme.identity.internal.domain.Workspace
import de.optadata.odil.learnwithme.identity.internal.oauth.OAuth2CodeExchangeService
import de.optadata.odil.learnwithme.identity.internal.persistence.LinkedIdentityRepository
import de.optadata.odil.learnwithme.identity.internal.persistence.RefreshTokenRepository
import de.optadata.odil.learnwithme.identity.internal.persistence.UserRepository
import de.optadata.odil.learnwithme.identity.internal.persistence.WorkspaceRepository
import de.optadata.odil.learnwithme.identity.internal.security.JwtIssuer
import de.optadata.odil.learnwithme.identity.internal.web.dto.TokenResponse
import de.optadata.odil.learnwithme.shared.web.ConflictException
import de.optadata.odil.learnwithme.shared.web.ForbiddenException
import de.optadata.odil.learnwithme.shared.web.UnauthorizedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val linkedIdentityRepository: LinkedIdentityRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtIssuer: JwtIssuer,
    private val oAuth2CodeExchangeService: OAuth2CodeExchangeService,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun register(email: String, password: String, displayName: String?): TokenResponse {
        val normalizedEmail = email.trim().lowercase()
        if (userRepository.findByEmailIgnoreCase(normalizedEmail) != null) {
            throw ConflictException("E-Mail-Adresse ist bereits registriert.")
        }
        val user = userRepository.save(
            User(
                email = normalizedEmail,
                displayName = displayName,
                passwordHash = passwordEncoder.encode(password),
            ),
        )
        val workspace = createDefaultWorkspace(user)
        return issueTokenPair(user.id, workspace.id, user.email)
    }

    @Transactional
    fun login(email: String, password: String): TokenResponse {
        val user = userRepository.findByEmailIgnoreCase(email.trim().lowercase())
            ?: throw UnauthorizedException("E-Mail oder Passwort ist falsch.")
        if (user.isDeleted) throw UnauthorizedException("E-Mail oder Passwort ist falsch.")
        val passwordHash = user.passwordHash
            ?: throw ForbiddenException("Dieses Konto wurde per SSO angelegt — bitte mit Google/GitHub anmelden.")
        if (!passwordEncoder.matches(password, passwordHash)) {
            throw UnauthorizedException("E-Mail oder Passwort ist falsch.")
        }
        val workspace = workspaceOf(user)
        return issueTokenPair(user.id, workspace.id, user.email)
    }

    @Transactional
    fun ssoLogin(provider: SsoProvider, code: String, codeVerifier: String, redirectUri: String): TokenResponse {
        val identity = oAuth2CodeExchangeService.exchange(provider, code, codeVerifier, redirectUri)

        val existingLink = linkedIdentityRepository.findByProviderAndProviderUid(provider, identity.providerUid)
        val user = if (existingLink != null) {
            userRepository.findById(existingLink.userId).orElseThrow {
                UnauthorizedException("Verknüpftes Konto existiert nicht mehr.")
            }
        } else {
            val normalizedEmail = identity.email.trim().lowercase()
            val byEmail = userRepository.findByEmailIgnoreCase(normalizedEmail)
            val user = byEmail ?: userRepository.save(
                User(email = normalizedEmail, displayName = identity.displayName),
            )
            linkedIdentityRepository.save(
                LinkedIdentity(userId = user.id, provider = provider, providerUid = identity.providerUid),
            )
            user
        }
        if (user.isDeleted) throw UnauthorizedException("Dieses Konto wurde gelöscht.")

        val workspace = workspaceOf(user)
        return issueTokenPair(user.id, workspace.id, user.email)
    }

    @Transactional
    fun refresh(rawRefreshToken: String): TokenResponse {
        val hash = hash(rawRefreshToken)
        val existing = refreshTokenRepository.findByTokenHash(hash)
            ?: throw UnauthorizedException("Refresh-Token ist ungültig.")
        if (!existing.isActive(Instant.now())) {
            throw UnauthorizedException("Refresh-Token ist abgelaufen oder wurde widerrufen.")
        }
        val user = userRepository.findById(existing.userId).orElseThrow {
            UnauthorizedException("Konto existiert nicht mehr.")
        }
        if (user.isDeleted) throw UnauthorizedException("Konto existiert nicht mehr.")

        val workspace = workspaceOf(user)
        val response = issueTokenPair(user.id, workspace.id, user.email)

        // Rotation: altes Token widerrufen und auf das neue verweisen.
        val newTokenId = refreshTokenRepository.findByTokenHash(hash(response.refreshToken))!!.id
        existing.revokedAt = Instant.now()
        existing.replacedById = newTokenId
        refreshTokenRepository.save(existing)

        return response
    }

    @Transactional
    fun logout(rawRefreshToken: String) {
        val existing = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)) ?: return
        if (existing.revokedAt == null) {
            existing.revokedAt = Instant.now()
            refreshTokenRepository.save(existing)
        }
    }

    private fun createDefaultWorkspace(user: User): Workspace =
        workspaceRepository.save(Workspace(ownerId = user.id, name = "${user.displayName ?: user.email}s Workspace"))

    private fun workspaceOf(user: User): Workspace =
        workspaceRepository.findByOwnerId(user.id) ?: createDefaultWorkspace(user)

    private fun issueTokenPair(userId: UUID, workspaceId: UUID, email: String): TokenResponse {
        val accessToken = jwtIssuer.issueAccessToken(userId, workspaceId, email)
        val rawRefreshToken = generateOpaqueToken()
        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = hash(rawRefreshToken),
                expiresAt = Instant.now().plus(JwtIssuer.REFRESH_TOKEN_TTL),
            ),
        )
        return TokenResponse(
            accessToken = accessToken.value,
            refreshToken = rawRefreshToken,
            expiresIn = JwtIssuer.ACCESS_TOKEN_TTL.seconds,
        )
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}
