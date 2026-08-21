package de.optadata.odil.learnwithme.shared

import java.util.UUID

/**
 * Authentifizierter Nutzer im Kontext seines Workspace (Tenant-Grenze, N9).
 * Wird vom Resource-Server-JWT abgeleitet ([wsid]/`sub`-Claim) und ist in jedem
 * Modul über `@AuthenticationPrincipal TenantPrincipal` verfügbar.
 */
data class TenantPrincipal(
    val userId: UUID,
    val workspaceId: UUID,
    val email: String,
)
