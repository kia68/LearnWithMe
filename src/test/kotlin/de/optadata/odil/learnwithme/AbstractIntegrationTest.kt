package de.optadata.odil.learnwithme

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
 * Gemeinsame Testcontainers-Infrastruktur für `@SpringBootTest`s (Epic G), extrahiert aus
 * [LearnWithMeApplicationTests] — siehe dort für die Begründung von Postgres-Image und MinIO.
 */
@SpringBootTest
@Testcontainers
abstract class AbstractIntegrationTest {

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
