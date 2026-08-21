package de.optadata.odil.learnwithme.identity.internal.security

import de.optadata.odil.learnwithme.shared.TenantPrincipal
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

/** Übersetzt das validierte Access-Token in einen [TenantPrincipal] (N9: Tenant-Kontext aus dem JWT). */
@Component
class TenantAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(source: Jwt): AbstractAuthenticationToken {
        val principal = TenantPrincipal(
            userId = UUID.fromString(source.subject),
            workspaceId = UUID.fromString(source.getClaimAsString("wsid")),
            email = source.getClaimAsString("email") ?: "",
        )
        return UsernamePasswordAuthenticationToken(principal, source, emptyList())
    }
}
