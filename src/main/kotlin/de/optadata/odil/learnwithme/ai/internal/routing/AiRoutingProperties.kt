package de.optadata.odil.learnwithme.ai.internal.routing

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

data class AiRoute(val provider: String, val model: String, val dimensions: Int? = null)

/** Bindet `learnwithme.ai-routing.*` (ADR-010) — Modell-Routing ist Konfiguration, nicht Code. */
@Component
@ConfigurationProperties(prefix = "learnwithme.ai-routing")
data class AiRoutingProperties(
    val conceptExtraction: AiRoute,
    val embedding: AiRoute,
    val itemGeneration: AiRoute,
    val groundednessJudge: AiRoute,
    val freeTextGrading: AiRoute,
    val errorAnalysis: AiRoute,
)
