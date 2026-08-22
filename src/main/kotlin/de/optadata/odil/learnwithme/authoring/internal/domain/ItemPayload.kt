package de.optadata.odil.learnwithme.authoring.internal.domain

data class ValidationIssue(val code: String, val message: String)

/**
 * Typspezifischer Payload eines Items (ADR-007). Bewusst OHNE Jackson-`@JsonTypeInfo`-Polymorphie:
 * `Item.type` trägt den Diskriminator bereits als eigene Spalte, De-/Serialisierung dispatcht
 * manuell darüber (`PayloadCodec`) — ein `when` ohne `else` erzwingt an dieser einen Stelle
 * Vollständigkeit, ohne Jacksons Typ-Auflösung (und ihre Sicherheits-/Versionsfallstricke) für
 * jede einzelne Payload-Klasse mitzuschleppen.
 */
sealed interface ItemPayload {
    fun validate(maxOptionLengthVarianceRatio: Double): List<ValidationIssue>
}

/** [misconceptionCategory] ist die Brücke zur Fehleranalyse ohne Laufzeit-LLM (Epic E, §11.5,
 * §12.2 Regel 4): eine der Taxonomie-Kategorien (FACTUAL_GAP|TERM_CONFUSION|CONCEPT_CONFUSION|
 * PROCEDURAL), vom Modell für jede falsche Option zugewiesen; bei der korrekten Option null. */
data class Option(
    val id: String,
    val text: String,
    val correct: Boolean,
    val rationale: String,
    val misconceptionCategory: String? = null,
)

private val FORBIDDEN_OPTION_PATTERNS = listOf(
    "alle der genannten", "alle genannten", "keine der genannten", "keine genannten",
    "all of the above", "none of the above",
)

private fun lengthVarianceRatio(options: List<Option>): Double {
    val lengths = options.map { it.text.trim().length }.filter { it > 0 }
    if (lengths.size < 2) return 1.0
    val shortest = lengths.min().toDouble()
    val longest = lengths.max().toDouble()
    if (shortest == 0.0) return Double.MAX_VALUE
    return longest / shortest
}

private fun validateMcOptions(options: List<Option>, expectedCorrect: IntRange, maxLengthVarianceRatio: Double): List<ValidationIssue> =
    buildList {
        val correctCount = options.count { it.correct }
        if (correctCount !in expectedCorrect) {
            add(ValidationIssue("CORRECT_COUNT", "Erwartete $expectedCorrect korrekte Optionen, gefunden $correctCount."))
        }
        if (options.size !in 3..6) {
            add(ValidationIssue("OPTION_COUNT", "Erwartet 3-6 Optionen, gefunden ${options.size}."))
        }
        if (options.map { it.text.trim().lowercase() }.distinct().size != options.size) {
            add(ValidationIssue("DUPLICATE_OPTIONS", "Optionstexte sind nicht eindeutig."))
        }
        if (options.any { opt -> FORBIDDEN_OPTION_PATTERNS.any { opt.text.contains(it, ignoreCase = true) } }) {
            add(ValidationIssue("FORBIDDEN_PATTERN", "Verbotenes Muster wie „alle/keine der genannten“."))
        }
        if (lengthVarianceRatio(options) > maxLengthVarianceRatio) {
            add(ValidationIssue("LENGTH_CUE", "Antwortlängen verraten vermutlich die richtige Option (C3)."))
        }
        if (options.any { it.rationale.isBlank() }) {
            add(ValidationIssue("MISSING_RATIONALE", "Jede Option braucht eine Begründung (C8)."))
        }
    }

data class McSinglePayload(val options: List<Option>, val shuffle: Boolean = true) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = validateMcOptions(options, 1..1, maxOptionLengthVarianceRatio)
}

data class McMultiPayload(val options: List<Option>, val shuffle: Boolean = true) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = validateMcOptions(options, 2..options.size, maxOptionLengthVarianceRatio)
}

data class TrueFalsePayload(val statement: String, val answer: Boolean, val rationale: String) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = buildList {
        if (statement.isBlank()) add(ValidationIssue("EMPTY_STATEMENT", "Aussage darf nicht leer sein."))
        if (rationale.isBlank()) add(ValidationIssue("MISSING_RATIONALE", "Begründung darf nicht leer sein (C8)."))
    }
}

data class OrderingElement(val id: String, val text: String)

data class OrderingPayload(
    val elements: List<OrderingElement>,
    val correctOrder: List<String>,
    val partialCredit: Boolean = true,
) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = buildList {
        if (elements.size < 3) add(ValidationIssue("TOO_FEW_ELEMENTS", "ORDERING braucht mindestens 3 Elemente."))
        val elementIds = elements.map { it.id }.toSet()
        if (correctOrder.toSet() != elementIds || correctOrder.size != elements.size) {
            add(ValidationIssue("ORDER_MISMATCH", "correctOrder muss exakt eine Permutation der Element-IDs sein."))
        }
    }
}

data class MatchingItem(val id: String, val text: String)
data class MatchingPair(val leftId: String, val rightId: String)

data class MatchingPayload(
    val left: List<MatchingItem>,
    val right: List<MatchingItem>,
    val pairs: List<MatchingPair>,
    val distractorsRight: List<MatchingItem> = emptyList(),
) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = buildList {
        if (left.size < 3) add(ValidationIssue("TOO_FEW_PAIRS", "MATCHING braucht mindestens 3 Paare."))
        if (pairs.size != left.size) add(ValidationIssue("PAIR_COUNT_MISMATCH", "Jedes linke Element braucht genau ein Paar."))
        val leftIds = left.map { it.id }.toSet()
        val rightIds = (right + distractorsRight).map { it.id }.toSet()
        if (pairs.any { it.leftId !in leftIds || it.rightId !in rightIds }) {
            add(ValidationIssue("UNKNOWN_PAIR_REFERENCE", "Ein Paar referenziert eine unbekannte ID."))
        }
        if (leftIds.size != left.size) add(ValidationIssue("DUPLICATE_LEFT_IDS", "Linke IDs sind nicht eindeutig."))
        if (rightIds.size != right.size + distractorsRight.size) add(ValidationIssue("DUPLICATE_RIGHT_IDS", "Rechte IDs sind nicht eindeutig."))
    }
}

data class ClozeBlank(val accepted: List<String>)

data class ClozePayload(val template: String, val blanks: List<ClozeBlank>) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = buildList {
        val placeholders = Regex("\\{\\{(\\d+)}}").findAll(template).map { it.groupValues[1].toInt() }.toSet()
        val expected = (1..blanks.size).toSet()
        if (placeholders != expected) {
            add(ValidationIssue("PLACEHOLDER_MISMATCH", "Template-Platzhalter {{1}}..{{n}} passen nicht zur Anzahl der Lücken."))
        }
        if (blanks.any { it.accepted.isEmpty() || it.accepted.all(String::isBlank) }) {
            add(ValidationIssue("EMPTY_BLANK", "Jede Lücke braucht mindestens eine akzeptierte Antwort."))
        }
    }
}

/** [points] ist die maximale Punktzahl für dieses Kriterium — Grundlage für den Rubric-Score
 * (Epic H, §10.1: `SHORT_ANSWER` → "rubric[{criterion,points}]"). */
data class RubricCriterion(val criterion: String, val points: Int)

data class ShortAnswerPayload(
    val rubric: List<RubricCriterion>,
    val referenceAnswer: String,
) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = buildList {
        if (rubric.isEmpty()) add(ValidationIssue("EMPTY_RUBRIC", "SHORT_ANSWER braucht mindestens ein Rubric-Kriterium."))
        if (rubric.any { it.criterion.isBlank() }) add(ValidationIssue("EMPTY_CRITERION", "Jedes Rubric-Kriterium braucht einen Text."))
        if (rubric.any { it.points <= 0 }) add(ValidationIssue("INVALID_POINTS", "Jedes Rubric-Kriterium braucht points > 0."))
        if (referenceAnswer.isBlank()) add(ValidationIssue("EMPTY_REFERENCE_ANSWER", "referenceAnswer darf nicht leer sein."))
    }
}

/** [unit] ist optional (z.B. „kg", „%") — wenn gesetzt, muss die Antwort sie tragen und sie fließt
 * mit ins Grading (§10.1: „Toleranz, Einheit"); `null` heißt: reine Zahl, keine Einheitsprüfung. */
data class NumericPayload(
    val value: Double,
    val tolerance: Double,
    val unit: String? = null,
) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = buildList {
        if (tolerance < 0) add(ValidationIssue("NEGATIVE_TOLERANCE", "tolerance darf nicht negativ sein."))
        if (unit != null && unit.isBlank()) add(ValidationIssue("BLANK_UNIT", "unit darf, wenn gesetzt, nicht leer sein."))
    }
}

data class CategorizationBucket(val id: String, val label: String)

/** [bucketId] ist die korrekte Zuordnung — beim Ausliefern an den Client vor der Antwort entfernt
 * (siehe `SessionController.stripAnswerFields`, Härtung nach Epic H). */
data class CategorizationElement(val id: String, val text: String, val bucketId: String)

data class CategorizationPayload(
    val buckets: List<CategorizationBucket>,
    val elements: List<CategorizationElement>,
) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = buildList {
        if (buckets.size < 2) add(ValidationIssue("TOO_FEW_BUCKETS", "CATEGORIZATION braucht mindestens 2 Kategorien."))
        if (elements.size < 3) add(ValidationIssue("TOO_FEW_ELEMENTS", "CATEGORIZATION braucht mindestens 3 Elemente."))
        val bucketIds = buckets.map { it.id }.toSet()
        if (bucketIds.size != buckets.size) add(ValidationIssue("DUPLICATE_BUCKET_IDS", "Kategorie-IDs sind nicht eindeutig."))
        val elementIds = elements.map { it.id }.toSet()
        if (elementIds.size != elements.size) add(ValidationIssue("DUPLICATE_ELEMENT_IDS", "Element-IDs sind nicht eindeutig."))
        if (elements.any { it.bucketId !in bucketIds }) {
            add(ValidationIssue("UNKNOWN_BUCKET_REFERENCE", "Ein Element referenziert eine unbekannte Kategorie."))
        }
    }
}

/** [expected] ist exakter (nur getrimmter) String-Vergleich — Code-Ausgabe ist oft
 * groß-/kleinschreibungssensitiv (z.B. Python `True` vs. `true`), anders als CLOZE (das
 * bewusst case-insensitive normalisiert, §10.2). */
data class CodeOutputPayload(
    val snippet: String,
    val language: String,
    val expected: String,
) : ItemPayload {
    override fun validate(maxOptionLengthVarianceRatio: Double) = buildList {
        if (snippet.isBlank()) add(ValidationIssue("EMPTY_SNIPPET", "snippet darf nicht leer sein."))
        if (language.isBlank()) add(ValidationIssue("EMPTY_LANGUAGE", "language darf nicht leer sein."))
        if (expected.isBlank()) add(ValidationIssue("EMPTY_EXPECTED", "expected darf nicht leer sein."))
    }
}
