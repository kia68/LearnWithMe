package de.optadata.odil.learnwithme.shared

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Zentrale Jackson-Instanz mit registriertem Kotlin-Modul. Ohne dieses Modul kann ein
 * `ObjectMapper` Kotlin-`data class`es mit `val`-Konstruktorparametern nicht zuverlässig über
 * den Primärkonstruktor deserialisieren (kein No-Arg-Konstruktor, keine Setter) — betrifft u.a.
 * `authoring.internal.domain.PayloadCodec` und die strukturierte LLM-Antwortverarbeitung
 * (`ai.internal.llm.SpringAiLlmGateway`).
 */
object JsonMapper {
    val instance: ObjectMapper = ObjectMapper().registerKotlinModule()
}
