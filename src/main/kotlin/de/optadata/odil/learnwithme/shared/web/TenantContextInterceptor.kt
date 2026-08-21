package de.optadata.odil.learnwithme.shared.web

import de.optadata.odil.learnwithme.shared.TenantContext
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Setzt [TenantContext] aus dem authentifizierten [TenantPrincipal] für die Dauer einer
 * HTTP-Anfrage (Epic G) — Voraussetzung dafür, dass die Postgres-RLS-Policies aus Migration V9
 * den richtigen Tenant sehen. Anonyme Endpunkte (Login/Register) haben keinen Principal;
 * [TenantContext] bleibt dann leer, [TenantAwareDataSource] setzt `app.workspace_id` auf `''`.
 */
@Component
class TenantContextInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? TenantPrincipal
        if (principal != null) TenantContext.set(principal.workspaceId)
        return true
    }

    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        TenantContext.clear()
    }
}
