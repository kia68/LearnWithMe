package de.optadata.odil.learnwithme.analytics.internal.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bindet `learnwithme.analytics.*` (§11.5). Registrierung über `@ConfigurationPropertiesScan`
 * (siehe `LearnWithMeApplication`), nicht `@Component` — siehe Epic-D/E-Fix in docs/progress.md.
 *
 * - [ambiguousThetaMargin]: θ_vorher > Item-Schwierigkeit + Margin ∧ falsch → `AMBIGUOUS_ITEM`.
 * - [carelessMaxElapsedMs]/[carelessMinExpectedSuccess]: `CARELESS`, wenn die erwartete
 *   Erfolgswahrscheinlichkeit hoch war, die Antwort sehr schnell kam, aber trotzdem falsch war.
 *   Fester Schwellwert statt Item-Median (Antwortzeit-Tracking existiert noch nicht, siehe
 *   docs/progress.md Epic D).
 * - [misconceptionThreshold]: ab dieser Anzahl gleicher Fehlerkategorie im selben Konzept gilt
 *   ein Misconception als "geflaggt" (E3) — kein separates Flag-Feld, siehe [Misconception][de.optadata.odil.learnwithme.analytics.internal.domain.Misconception].
 */
@ConfigurationProperties(prefix = "learnwithme.analytics")
data class AnalyticsProperties(
    val ambiguousThetaMargin: Float = 1.5f,
    val carelessMaxElapsedMs: Int = 3000,
    val carelessMinExpectedSuccess: Float = 0.85f,
    val misconceptionThreshold: Int = 3,
)
