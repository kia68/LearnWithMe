package de.optadata.odil.learnwithme.platform

import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * N14 (RPO/RTO): beweist `pg_dump`/`psql`-Restore funktioniert gegen das echte, migrierte Schema
 * inkl. der pgvector-Extension (ADR-002) — kein Aufbau echter PITR-Infrastruktur (kein Story-Bedarf
 * in Epic G, siehe docs/progress.md), sondern ein automatisierbarer Nachweis, dass Dump+Restore
 * mechanisch funktioniert und Daten (inkl. Vektor-Spalten) verlustfrei überstehen.
 *
 * Migration V9 aktiviert RLS auf `sources`/`concepts` — jede rohe JDBC-Verbindung hier setzt
 * daher `app.workspace_id` selbst (kein Spring-Kontext/`TenantAwareDataSource` in diesem Test).
 */
@Testcontainers
class BackupRestoreProbeTest {

    private val log = LoggerFactory.getLogger(BackupRestoreProbeTest::class.java)
    private val workspaceId = UUID.randomUUID()

    @Test
    fun `dump from a migrated database restores into a fresh one with identical data`(@TempDir tempDir: Path) {
        PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres")).use { source ->
            source.start()
            source.followOutput(Slf4jLogConsumer(log))
            migrate(source)
            seedData(source)

            val dumpFile = tempDir.resolve("dump.sql")
            source.execInContainer(
                "pg_dump", "-U", source.username, "-d", source.databaseName,
                "--no-owner", "--no-privileges", "-f", "/tmp/dump.sql",
            ).also { it.exitCode shouldBe 0 }
            source.copyFileFromContainer("/tmp/dump.sql", dumpFile.toString())

            PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres")).use { target ->
                target.start()
                target.copyFileToContainer(MountableFile.forHostPath(dumpFile), "/tmp/dump.sql")
                target.execInContainer("psql", "-U", target.username, "-d", target.databaseName, "-f", "/tmp/dump.sql")
                    .also { it.exitCode shouldBe 0 }

                assertSameData(source, target)
            }
        }
    }

    private fun migrate(container: PostgreSQLContainer<*>) {
        Flyway.configure()
            .dataSource(container.jdbcUrl, container.username, container.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    private fun connect(container: PostgreSQLContainer<*>): Connection {
        val conn = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
        conn.prepareStatement("SELECT set_config('app.workspace_id', ?, false)").use { ps ->
            ps.setString(1, workspaceId.toString())
            ps.execute()
        }
        return conn
    }

    private fun seedData(container: PostgreSQLContainer<*>) {
        connect(container).use { conn ->
            conn.createStatement().use { st ->
                st.execute("INSERT INTO users (id, email) VALUES (gen_random_uuid(), 'probe@example.com')")
                st.execute(
                    "INSERT INTO workspaces (id, owner_id, name) " +
                        "VALUES ('$workspaceId', (SELECT id FROM users LIMIT 1), 'probe-ws')",
                )
                st.execute(
                    "INSERT INTO sources (id, workspace_id, kind, title, content_hash, status) " +
                        "VALUES (gen_random_uuid(), '$workspaceId', 'TEXT', 'probe', decode('00','hex'), 'READY')",
                )
                st.execute(
                    "INSERT INTO concepts (id, workspace_id, source_id, name, summary, embedding) " +
                        "VALUES (gen_random_uuid(), '$workspaceId', (SELECT id FROM sources LIMIT 1), " +
                        "'probe-concept', 'summary', (SELECT ('[' || array_to_string(array(SELECT 0.1 FROM generate_series(1,1536)), ',') || ']')::vector))",
                )
            }
        }
    }

    private fun assertSameData(source: PostgreSQLContainer<*>, target: PostgreSQLContainer<*>) {
        for (table in listOf("users", "workspaces", "sources", "concepts")) {
            countRows(source, table) shouldBe countRows(target, table)
        }
        embeddingText(source) shouldBe embeddingText(target)
    }

    private fun countRows(container: PostgreSQLContainer<*>, table: String): Int =
        connect(container).use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT count(*) FROM $table").use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun embeddingText(container: PostgreSQLContainer<*>): String =
        connect(container).use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT embedding::text FROM concepts LIMIT 1").use { rs ->
                    rs.next()
                    rs.getString(1)
                }
            }
        }
}
