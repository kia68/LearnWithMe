package de.optadata.odil.learnwithme.assessment.internal.config

import org.springframework.boot.context.properties.ConfigurationProperties

data class SelectionTuning(val softmaxTemperature: Float, val recentItemWindow: Int, val maxSameTypeInARow: Int)

/** Bindet die Item-Auswahl-Policy-Parameter (§11.3) aus `learnwithme.adaptivity.*`. Bewusst hier
 * und nicht in `adaptivity.internal.config.AdaptivityProperties` gebunden: die Auswahl selbst
 * (Kandidatenpool, Softmax, Typ-Rotation) ist Sache von `assessment`, nicht von `adaptivity`
 * (§6.3 — `adaptivity` kennt keine Items), auch wenn beide dieselbe YAML-Wurzel teilen.
 * Registrierung über `@ConfigurationPropertiesScan` (siehe LearnWithMeApplication), nicht
 * `@Component` — sonst greift für den verschachtelten `SelectionTuning`-Parameter normale
 * Konstruktor-Autowiring-Auflösung statt Properties-Binding. */
@ConfigurationProperties(prefix = "learnwithme.adaptivity")
data class SelectionProperties(
    val targetSuccessProbability: Float,
    val selection: SelectionTuning,
)
