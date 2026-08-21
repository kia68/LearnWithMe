package de.optadata.odil.learnwithme.authoring.internal.generation

import de.optadata.odil.learnwithme.authoring.internal.domain.ItemType

/** C1/C8: Prompt-Bausteine je Fragetyp. Fordert explizit JSON an (statt Spring-AI-Structured-Output,
 * siehe [de.optadata.odil.learnwithme.ai.internal.llm.SpringAiLlmGateway]-Kommentar) und verlangt
 * für jede Distraktor-Option eine Begründung (C8). */
object PromptBuilder {

    fun systemPrompt(type: ItemType): String = buildString {
        appendLine("Du generierst genau eine Lernfrage auf Deutsch, AUSSCHLIESSLICH basierend auf dem im User-Prompt gegebenen Textausschnitt.")
        appendLine("Antworte NUR mit validem JSON — keine Markdown-Codeblöcke, kein Text vor oder nach dem JSON.")
        appendLine("Erfinde keine Fakten, die nicht direkt im Ausschnitt stehen.")
        appendLine("Jede falsche Option/jeder falsche Fall braucht eine eigene, plausible Begründung, warum sie falsch ist (nicht nur, warum die richtige Antwort richtig ist).")
        appendLine("bloomLevel ist eines von: REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE.")
        appendLine("Vermeide Optionen wie \"alle der genannten\" oder \"keine der genannten\".")
        appendLine("Variiere die Antwortlängen nicht systematisch — die Länge einer Option darf kein Hinweis auf Richtigkeit sein.")
        append(schemaHint(type))
    }

    private fun schemaHint(type: ItemType): String = when (type) {
        ItemType.MC_SINGLE ->
            """JSON-Schema: {"stem": string, "explanation": string, "bloomLevel": string, "options": [{"id": string, "text": string, "correct": boolean, "rationale": string}, ...]}. Genau EINE Option mit correct=true, insgesamt 3-6 Optionen."""
        ItemType.MC_MULTI ->
            """JSON-Schema: {"stem": string, "explanation": string, "bloomLevel": string, "options": [{"id": string, "text": string, "correct": boolean, "rationale": string}, ...]}. Mindestens ZWEI Optionen mit correct=true, insgesamt 3-6 Optionen."""
        ItemType.TRUE_FALSE ->
            """JSON-Schema: {"stem": string, "explanation": string, "bloomLevel": string, "statement": string, "answer": boolean, "rationale": string}."""
        ItemType.ORDERING ->
            """JSON-Schema: {"stem": string, "explanation": string, "bloomLevel": string, "elements": [{"id": string, "text": string}, ...], "correctOrder": [string, ...]}. Mindestens 3 Elemente; correctOrder referenziert alle Element-IDs in der korrekten Reihenfolge."""
        ItemType.MATCHING ->
            """JSON-Schema: {"stem": string, "explanation": string, "bloomLevel": string, "left": [{"id","text"}, ...], "right": [{"id","text"}, ...], "pairs": [{"leftId","rightId"}, ...], "distractorsRight": [{"id","text"}, ...]}. Mindestens 3 Paare; distractorsRight sind optionale rechte Einträge ohne Paar (Ablenker)."""
        ItemType.CLOZE ->
            """JSON-Schema: {"stem": string, "explanation": string, "bloomLevel": string, "template": "Lückentext mit {{1}}, {{2}}, ...", "blanks": [{"accepted": [string, ...]}, ...]}. Platzhalter-Nummerierung beginnt bei 1 und ist lückenlos fortlaufend."""
    }

    fun userPrompt(conceptName: String, conceptSummary: String, chunkText: String): String = buildString {
        appendLine("Konzept: $conceptName")
        if (conceptSummary.isNotBlank()) appendLine("Zusammenfassung: $conceptSummary")
        appendLine()
        appendLine("Textausschnitt (einzige erlaubte Quelle für Fakten):")
        appendLine("\"\"\"")
        appendLine(chunkText)
        appendLine("\"\"\"")
    }
}
