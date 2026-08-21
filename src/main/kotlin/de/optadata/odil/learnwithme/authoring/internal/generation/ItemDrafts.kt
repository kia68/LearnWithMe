package de.optadata.odil.learnwithme.authoring.internal.generation

import de.optadata.odil.learnwithme.authoring.internal.domain.BloomLevel
import de.optadata.odil.learnwithme.authoring.internal.domain.ClozeBlank
import de.optadata.odil.learnwithme.authoring.internal.domain.MatchingItem
import de.optadata.odil.learnwithme.authoring.internal.domain.MatchingPair
import de.optadata.odil.learnwithme.authoring.internal.domain.Option
import de.optadata.odil.learnwithme.authoring.internal.domain.OrderingElement

/**
 * Flache, typspezifische LLM-Antwortstruktur pro [de.optadata.odil.learnwithme.authoring.internal.domain.ItemType]
 * (C1). Flach statt eines generischen `Draft<Payload>`-Wrappers: Jackson-Generics über
 * `Class<T>`-Ziele sind wegen Typlöschung fragil, und ein flaches JSON-Schema ist für das LLM
 * ohnehin einfacher zuverlässig zu treffen als verschachtelte Objekte.
 */
data class McSingleDraft(val stem: String, val explanation: String, val bloomLevel: BloomLevel, val options: List<Option>)

data class McMultiDraft(val stem: String, val explanation: String, val bloomLevel: BloomLevel, val options: List<Option>)

data class TrueFalseDraft(
    val stem: String,
    val explanation: String,
    val bloomLevel: BloomLevel,
    val statement: String,
    val answer: Boolean,
    val rationale: String,
)

data class OrderingDraft(
    val stem: String,
    val explanation: String,
    val bloomLevel: BloomLevel,
    val elements: List<OrderingElement>,
    val correctOrder: List<String>,
)

data class MatchingDraft(
    val stem: String,
    val explanation: String,
    val bloomLevel: BloomLevel,
    val left: List<MatchingItem>,
    val right: List<MatchingItem>,
    val pairs: List<MatchingPair>,
    val distractorsRight: List<MatchingItem> = emptyList(),
)

data class ClozeDraft(
    val stem: String,
    val explanation: String,
    val bloomLevel: BloomLevel,
    val template: String,
    val blanks: List<ClozeBlank>,
)
