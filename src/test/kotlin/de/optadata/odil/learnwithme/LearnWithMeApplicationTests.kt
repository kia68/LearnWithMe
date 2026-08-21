package de.optadata.odil.learnwithme

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * `spring-boot-docker-compose` (compose.yaml, lokales Profil) ist bewusst `developmentOnly`
 * (build.gradle.kts) und deshalb NICHT auf dem Testklassenpfad — das ist Absicht (Docker Compose
 * ist für `bootRun`, nicht für Tests), heißt aber, `@SpringBootTest` braucht seine eigene
 * Infrastruktur:
 * - Postgres: `pgvector/pgvector:pg17` statt des Testcontainers-Default-`postgres`-Images, weil
 *   die V3-Migration `CREATE EXTENSION vector` ausführt (ADR-002).
 * - MinIO: `S3StorageService.ensureBucketExists()` (§6.2) verbindet sich bei jedem Start per
 *   `@PostConstruct` zum Objektspeicher — ohne erreichbaren Endpunkt scheitert der Context-Start
 *   unabhängig von der Datenbank. Kein Spring-Boot-`@ServiceConnection` für S3/MinIO vorhanden
 *   (kein Erstanbieter-Konzept wie bei JDBC) — Endpunkt/Credentials daher über
 *   `@DynamicPropertySource` gesetzt, analog zu `learnwithme.storage.*` in application.yml.
 */
@SpringBootTest
@Testcontainers
class LearnWithMeApplicationTests {

    @Test
    fun contextLoads() {
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"),
        )

        @Container
        @JvmStatic
        val minio: GenericContainer<*> = GenericContainer(DockerImageName.parse("minio/minio:latest"))
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "learnwithme")
            .withEnv("MINIO_ROOT_PASSWORD", "learnwithme")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000))

        @DynamicPropertySource
        @JvmStatic
        fun storageProperties(registry: DynamicPropertyRegistry) {
            registry.add("learnwithme.storage.endpoint") { "http://${minio.host}:${minio.getMappedPort(9000)}" }
            registry.add("learnwithme.storage.access-key") { "learnwithme" }
            registry.add("learnwithme.storage.secret-key") { "learnwithme" }
        }
    }
}
