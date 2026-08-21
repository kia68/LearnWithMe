package de.optadata.odil.learnwithme.content.internal.chunking

import kotlin.math.ceil

/**
 * Grobe Tokenschätzung ohne echten Tokenizer (~4 Zeichen/Token, gängige Heuristik für
 * gemischten DE/EN-Text). Reicht für Chunk-Größensteuerung; ein echter, providerspezifischer
 * Tokenizer folgt erst mit dem `LlmGateway` (M1) — bis dahin würde jede Genauigkeit hier nur
 * Schein-Präzision vortäuschen.
 */
object TokenEstimator {
    const val CHARS_PER_TOKEN = 4.0

    fun estimate(text: String): Int = ceil(text.length / CHARS_PER_TOKEN).toInt().coerceAtLeast(if (text.isEmpty()) 0 else 1)
}
