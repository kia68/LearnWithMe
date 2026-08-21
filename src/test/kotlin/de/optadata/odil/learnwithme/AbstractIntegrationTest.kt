package de.optadata.odil.learnwithme

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * Gemeinsame Testcontainers-Infrastruktur für `@SpringBootTest`s (Epic G), extrahiert aus
 * [LearnWithMeApplicationTests] — siehe dort für die Begründung von Postgres-Image und MinIO.
 *
 * Bewusst **kein** `@Testcontainers`/`@Container` (Testcontainers' "Singleton Containers"-Muster
 * statt des `@Container`-Lifecycles): `@Container` bindet Start/Stop an die JUnit-Extension der
 * jeweiligen *Testklasse* — bei einer in der Basisklasse geteilten Companion-Object-Instanz stoppt
 * das den Container nach der ersten Subklasse (hier zuerst `LearnWithMeApplicationTests`), obwohl
 * `RowLevelSecurityTest` ihn danach noch braucht (`ConnectException`, da bereits gestoppt). Manuelles
 * `.start()` unten läuft nur einmal (Kotlin-`object`-Initialisierung ist selbst thread-sicher/einmalig)
 * und wird nie von JUnit wieder gestoppt — stirbt einfach mit der Test-JVM.
 *
 * Bewusst **kein** `@ServiceConnection` (Epic G): `application.yml` trennt seit V9
 * `spring.datasource.*` (App-Traffic, Rolle `learnwithme_app`) von `spring.flyway.*`
 * (Migrationen, Bootstrap-Superuser) — `@ServiceConnection` verdrahtet aber nur *eine* Rolle
 * (die des Containers) automatisch in `spring.datasource.*`, und Boot lässt explizit gesetzte
 * `spring.flyway.*`-Properties (wie in `application.yml`) nicht von `ServiceConnection`
 * überschreiben. Beide Verbindungen daher hier explizit über `@DynamicPropertySource`: Flyway
 * bekommt die echte Container-URL + dessen Bootstrap-Superuser, `spring.datasource.*` dieselbe
 * URL mit `learnwithme_app`/demselben Dev-Passwort wie in V9.
 */
@SpringBootTest
abstract class AbstractIntegrationTest {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"),
        ).also { it.start() }

        @JvmStatic
        val minio: GenericContainer<*> = GenericContainer(DockerImageName.parse("minio/minio:latest"))
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "learnwithme")
            .withEnv("MINIO_ROOT_PASSWORD", "learnwithme")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000))
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun storageProperties(registry: DynamicPropertyRegistry) {
            registry.add("learnwithme.storage.endpoint") { "http://${minio.host}:${minio.getMappedPort(9000)}" }
            registry.add("learnwithme.storage.access-key") { "learnwithme" }
            registry.add("learnwithme.storage.secret-key") { "learnwithme" }
        }

        @DynamicPropertySource
        @JvmStatic
        fun tenantAwareDatasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "learnwithme_app" }
            registry.add("spring.datasource.password") { "learnwithme_app_dev" }
        }
    }
}
