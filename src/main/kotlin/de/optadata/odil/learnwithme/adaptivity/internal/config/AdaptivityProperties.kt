package de.optadata.odil.learnwithme.adaptivity.internal.config

import org.springframework.boot.context.properties.ConfigurationProperties

data class EloProperties(val userKA: Float, val userKB: Float, val itemKA: Float, val itemKB: Float)

data class FsrsProperties(val targetRetention: Double)

/** Bindet `learnwithme.adaptivity.{elo,fsrs}` (§11.2/§11.4) — die reinen Modell-Parameter des
 * `adaptivity`-Moduls. `learnwithme.adaptivity.{target-success-probability,selection}` gehören zur
 * Item-Auswahl-Policy und werden bewusst dort gebunden, wo sie gebraucht werden: in
 * `assessment.internal.config.SelectionProperties` — `adaptivity` kennt keine Items (§6.3).
 * Registrierung über `@ConfigurationPropertiesScan` (siehe LearnWithMeApplication), nicht
 * `@Component` — sonst greift für die verschachtelten `EloProperties`/`FsrsProperties`-Parameter
 * normale Konstruktor-Autowiring-Auflösung statt Properties-Binding. */
@ConfigurationProperties(prefix = "learnwithme.adaptivity")
data class AdaptivityProperties(
    val elo: EloProperties,
    val fsrs: FsrsProperties,
)
