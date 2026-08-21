package de.optadata.odil.learnwithme.shared

import java.util.UUID

/**
 * Thread-lokaler Tenant-Kontext für Postgres Row-Level-Security (N9, zweite Verteidigungslinie
 * neben dem bestehenden Tenant-Filter in jeder Query, Epic G). [TenantAwareDataSource] liest ihn
 * bei jedem Connection-Checkout und setzt `app.workspace_id` in der DB-Session entsprechend.
 *
 * Gesetzt von `shared.web.TenantContextInterceptor` (HTTP-Anfragen) bzw. `JobWorker`
 * (Hintergrund-Jobs) — beide MÜSSEN in einem `finally`-Block wieder [clear]en, da sowohl
 * Tomcat- als auch Scheduler-Threads wiederverwendet werden.
 */
object TenantContext {
    private val holder = ThreadLocal<UUID?>()

    fun set(workspaceId: UUID) = holder.set(workspaceId)
    fun current(): UUID? = holder.get()
    fun clear() = holder.remove()
}
