package de.optadata.odil.learnwithme

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * `spring-boot-docker-compose` (compose.yaml, lokales Profil) ist bewusst `developmentOnly`
 * (build.gradle.kts) und deshalb NICHT auf dem Testklassenpfad — das ist Absicht (Docker Compose
 * ist für `bootRun`, nicht für Tests), heißt aber, `@SpringBootTest` braucht seine eigene
 * Datenbank. `pgvector/pgvector:pg17` statt des Testcontainers-Default-`postgres`-Images, weil
 * V3-Migration `CREATE EXTENSION vector` ausführt (ADR-002) — ohne die Extension im Image schlägt
 * Flyway fehl, bevor Hibernate überhaupt validiert.
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
    }
}
