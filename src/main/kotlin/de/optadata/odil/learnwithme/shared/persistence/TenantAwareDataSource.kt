package de.optadata.odil.learnwithme.shared.persistence

import de.optadata.odil.learnwithme.shared.TenantContext
import java.sql.Connection
import javax.sql.DataSource

/**
 * Setzt bei jedem Connection-Checkout die Postgres-Session-Variable `app.workspace_id` aus
 * [TenantContext] (Epic G, N9 zweite Verteidigungslinie via RLS, Migration V9).
 *
 * `set_config(..., false)` statt eines rohen `SET`-Statements, damit der Wert per
 * JDBC-Parameter gebunden werden kann (kein String-Interpolations-Risiko). Läuft
 * [TenantContext.current] leer, wird explizit auf `''` gesetzt (nicht einfach übersprungen) —
 * sonst würde eine wiederverwendete Pool-Connection den Tenant der vorherigen Anfrage behalten.
 * Die RLS-Policies vergleichen `workspace_id::text` gegen diesen Wert, daher ist `''` sicher
 * (vergleicht nie gleich einer echten UUID) und wirft anders als ein `::uuid`-Cast nie einen
 * Laufzeitfehler.
 */
class TenantAwareDataSource(private val delegate: DataSource) : DataSource by delegate {

    override fun getConnection(): Connection = applyTenant(delegate.connection)

    override fun getConnection(username: String, password: String): Connection =
        applyTenant(delegate.getConnection(username, password))

    private fun applyTenant(connection: Connection): Connection {
        connection.prepareStatement("SELECT set_config('app.workspace_id', ?, false)").use { statement ->
            statement.setString(1, TenantContext.current()?.toString() ?: "")
            statement.execute()
        }
        return connection
    }
}
