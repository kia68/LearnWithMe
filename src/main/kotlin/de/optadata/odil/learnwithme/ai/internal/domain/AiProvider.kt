package de.optadata.odil.learnwithme.ai.internal.domain

/** Unterstützte LLM-Provider für BYOK (A3, ADR-004/ADR-010). */
enum class AiProvider(val displayName: String, val requiresBaseUrl: Boolean) {
    OPENAI("OpenAI", requiresBaseUrl = false),
    ANTHROPIC("Anthropic", requiresBaseUrl = false),
    GOOGLE("Google", requiresBaseUrl = false),
    AZURE("Azure OpenAI", requiresBaseUrl = true),
    OPENROUTER("OpenRouter", requiresBaseUrl = false),
    OLLAMA("Ollama (lokal)", requiresBaseUrl = true),
}
