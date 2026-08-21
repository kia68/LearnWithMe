plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)      // all-open für @Component, @Transactional …
    alias(libs.plugins.kotlin.jpa)         // no-arg + all-open für @Entity
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.mgmt)
}

group = "de.optadata.odil"
version = "0.0.1-SNAPSHOT"
description = "LearnWithMe — adaptive, KI-gestützte Lernplattform"

// Der Projektordner liegt unter OneDrive. OneDrives Sync-Prozess hält Datei-Handles auf
// build/-Artefakte offen und blockiert dadurch Gradle-Rebuilds ("Unable to delete directory").
// Build-Output deshalb außerhalb des synchronisierten Baums ablegen.
System.getenv("LOCALAPPDATA")?.let { localAppData ->
    layout.buildDirectory.set(file("$localAppData/gradle-builds/learnWithMe"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    // ⚠ Nur nötig, solange Spring AI 2.x als Milestone bezogen wird.
    //   Nach Wechsel auf eine GA-Version diesen Block entfernen.
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
    imports {
        mavenBom(libs.modulith.bom.get().toString())
        mavenBom(libs.spring.ai.bom.get().toString())
        mavenBom(libs.testcontainers.bom.get().toString())
        mavenBom(libs.awssdk.bom.get().toString())
    }
}

dependencies {
    // ── Web & API ────────────────────────────────────────────────────────────
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.webmvc.ui)              // OpenAPI = Single Source of Truth (ADR-011)

    // ── Sicherheit ───────────────────────────────────────────────────────────
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.rs)
    implementation(libs.spring.boot.starter.oauth2.client)   // Google/GitHub Code-Exchange (A1)

    // ── Persistenz ───────────────────────────────────────────────────────────
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.flyway)               // Boot 4: FlywayAutoConfiguration ist ein eigenes Artefakt
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.pgvector)                         // Vektor-Typ für JPA (ADR-002)
    runtimeOnly(libs.postgresql)

    // ── Modularer Monolith (ADR-001) ─────────────────────────────────────────
    // spring-modulith-starter-jpa aktiviert u. a. die JPA-basierte
    // Event-Publication-Registry → unser transaktionales Outbox (ADR-012).
    implementation(libs.modulith.starter.core)
    implementation(libs.modulith.starter.jpa)
    implementation(libs.modulith.actuator)
    runtimeOnly(libs.modulith.observability)

    // ── KI (ADR-004) ─────────────────────────────────────────────────────────
    // ⚠ Diese Artefakte dürfen ausschließlich im Paket `…learnwithme.ai.internal`
    //   referenziert werden. Der ArchUnit-Test `AiIsolationTest` erzwingt das.
    implementation(libs.spring.ai.openai)
    implementation(libs.spring.ai.anthropic)
    implementation(libs.spring.ai.ollama)
    implementation(libs.spring.ai.pgvector)

    // ── Content-Extraktion (M1) ──────────────────────────────────────────────
    implementation(libs.tika.core)
    implementation(libs.tika.parsers)                     // bringt PDFBox, POI … transitiv
    implementation(libs.jsoup)
    implementation(libs.readability4j)                    // Mozilla-Readability-Port, §14.3

    // ── Objektspeicher (Epic B) ──────────────────────────────────────────────
    implementation(libs.awssdk.s3)                         // S3-kompatibel: MinIO lokal / Hetzner-S3 prod

    // ── Kotlin ───────────────────────────────────────────────────────────────
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.module.kotlin.classic)     // s. Kommentar in libs.versions.toml
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)

    // ── Betrieb ──────────────────────────────────────────────────────────────
    implementation(libs.spring.boot.starter.actuator)
    developmentOnly(libs.spring.boot.docker.compose)      // startet compose.yaml beim App-Start

    // ── Test ─────────────────────────────────────────────────────────────────
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.modulith.starter.test)        // @ApplicationModuleTest
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit5)              // Architekturregeln (N11)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// Architektur- und Modultests laufen getrennt, damit sie im CI eigenständig
// rot werden können (siehe docs/PLAN.md §17.1).
tasks.register<Test>("architectureTest") {
    description = "Prüft Modulgrenzen (Spring Modulith + ArchUnit)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("architecture") }
    shouldRunAfter(tasks.test)
}
