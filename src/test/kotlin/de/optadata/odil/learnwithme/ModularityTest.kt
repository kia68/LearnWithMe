package de.optadata.odil.learnwithme

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/** Erzwingt die Modulgrenzen aus docs/PLAN.md §6.3 (ADR-001): kein Modul darf `internal`
 * eines anderen Moduls referenzieren, Zyklen sind verboten. Rein statische Analyse der
 * kompilierten Klassen — braucht keinen Spring-Kontext, keine DB. */
@Tag("architecture")
class ModularityTest {

    @Test
    fun `module boundaries are respected`() {
        ApplicationModules.of(LearnWithMeApplication::class.java).verify()
    }
}
