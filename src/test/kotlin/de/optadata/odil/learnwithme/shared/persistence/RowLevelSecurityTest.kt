package de.optadata.odil.learnwithme.shared.persistence

import de.optadata.odil.learnwithme.AbstractIntegrationTest
import de.optadata.odil.learnwithme.shared.TenantContext
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Epic G, N9: beweist die Postgres-RLS-Policies aus Migration V9 unabhängig vom
 * Anwendungs-Tenant-Filter — bewusst über eine rohe Query OHNE `WHERE workspace_id = ...`,
 * genau das Szenario, gegen das RLS als zweite Verteidigungslinie schützen soll.
 */
class RowLevelSecurityTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun clearTenant() {
        TenantContext.clear()
    }

    @Test
    fun `raw query without a workspace filter still only sees the active tenant's rows`() {
        val (workspaceA, sourceA) = createWorkspaceWithSource("workspace-a@example.com")
        val (workspaceB, sourceB) = createWorkspaceWithSource("workspace-b@example.com")

        TenantContext.set(workspaceA)
        val visibleToA = jdbcTemplate.queryForList("SELECT id FROM sources", UUID::class.java)

        TenantContext.set(workspaceB)
        val visibleToB = jdbcTemplate.queryForList("SELECT id FROM sources", UUID::class.java)

        visibleToA shouldBe listOf(sourceA)
        visibleToB shouldBe listOf(sourceB)
    }

    @Test
    fun `no tenant context set means no rows visible (default-deny)`() {
        createWorkspaceWithSource("workspace-c@example.com")
        TenantContext.clear()

        val visible = jdbcTemplate.queryForList("SELECT id FROM sources", UUID::class.java)

        visible.isEmpty() shouldBe true
    }

    @Test
    fun `inserting a row under a different tenant's context is rejected`() {
        val workspaceA = UUID.randomUUID()
        createUserAndWorkspace(workspaceA, "workspace-d@example.com")
        val workspaceB = UUID.randomUUID()
        createUserAndWorkspace(workspaceB, "workspace-e@example.com")

        TenantContext.set(workspaceA)
        runCatching {
            jdbcTemplate.update(
                "INSERT INTO sources (id, workspace_id, kind, title, content_hash, status) VALUES (?, ?, 'TEXT', 'x', decode('00','hex'), 'READY')",
                UUID.randomUUID(),
                workspaceB,
            )
        }.isFailure shouldBe true
    }

    private fun createWorkspaceWithSource(email: String): Pair<UUID, UUID> {
        val workspaceId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        createUserAndWorkspace(workspaceId, email)
        TenantContext.set(workspaceId)
        jdbcTemplate.update(
            "INSERT INTO sources (id, workspace_id, kind, title, content_hash, status) VALUES (?, ?, 'TEXT', 'x', decode('00','hex'), 'READY')",
            sourceId,
            workspaceId,
        )
        return workspaceId to sourceId
    }

    private fun createUserAndWorkspace(workspaceId: UUID, email: String) {
        val userId = UUID.randomUUID()
        TenantContext.clear()
        jdbcTemplate.update("INSERT INTO users (id, email) VALUES (?, ?)", userId, email)
        jdbcTemplate.update("INSERT INTO workspaces (id, owner_id, name) VALUES (?, ?, 'ws')", workspaceId, userId)
    }
}
