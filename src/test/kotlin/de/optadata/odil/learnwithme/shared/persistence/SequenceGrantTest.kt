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
 * V11: `V9`s `GRANT ... ON ALL TABLES` deckt keine Sequenzen ab — jede der drei Tabellen mit
 * `BIGSERIAL`-PK statt UUID (`llm_usage`, `attempts`, `error_events`) scheiterte unter der
 * `learnwithme_app`-Rolle mit "permission denied for sequence ..." (entdeckt bei der Live-
 * Verifikation der M6-Fragetypen gegen den echten Dev-Stack — kein existierender Test hatte das
 * bis dahin je unter der App-Rolle exerciert; `RowLevelSecurityTest` testet ausschließlich
 * `sources`, eine UUID-PK-Tabelle ohne Sequenz). `llm_usage` hier als Stellvertreter, weil es als
 * einziges der drei keine FK-Kette (Source→Concept→Chunk→Item bzw. Session) zum Aufbauen braucht —
 * der Fix (`GRANT USAGE ON ALL SEQUENCES`) ist tabellenunabhängig, ein Nachweis genügt.
 */
class SequenceGrantTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun clearTenant() {
        TenantContext.clear()
    }

    @Test
    fun `inserting into a BIGSERIAL-PK table under the app role succeeds`() {
        val userId = UUID.randomUUID()
        val workspaceId = UUID.randomUUID()
        jdbcTemplate.update("INSERT INTO users (id, email) VALUES (?, ?)", userId, "seq-grant@example.com")
        jdbcTemplate.update("INSERT INTO workspaces (id, owner_id, name) VALUES (?, ?, 'ws')", workspaceId, userId)

        TenantContext.set(workspaceId)
        val id = jdbcTemplate.queryForObject(
            """INSERT INTO llm_usage (workspace_id, task, provider, model, input_tokens, output_tokens, cost_micros, latency_ms, outcome)
               VALUES (?, 'ITEM_GENERATION', 'openai', 'gpt-4o-mini', 10, 20, 100, 500, 'OK') RETURNING id""",
            Long::class.java,
            workspaceId,
        )

        // Die erfolgreiche RETURNING-id (aus nextval() auf `llm_usage_id_seq`) beweist den Grant;
        // der Re-Read über RLS beweist zusätzlich, dass die Zeile unter demselben Tenant sichtbar ist.
        jdbcTemplate.queryForObject("SELECT id FROM llm_usage WHERE id = ?", Long::class.java, id) shouldBe id
    }
}
