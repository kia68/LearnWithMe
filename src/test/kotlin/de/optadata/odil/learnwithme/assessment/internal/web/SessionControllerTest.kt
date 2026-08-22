package de.optadata.odil.learnwithme.assessment.internal.web

import de.optadata.odil.learnwithme.assessment.internal.selection.SelectedItem
import de.optadata.odil.learnwithme.assessment.internal.service.AttemptService
import de.optadata.odil.learnwithme.assessment.internal.service.SessionService
import de.optadata.odil.learnwithme.shared.JsonMapper
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.UUID

/** Härtung (siehe `docs/progress.md` „Bekannte Lücken" Epic H): `GET /sessions/{id}/next` (und
 * jeder andere Pfad, der über `SelectedItem.toResponse()` läuft) darf, bevor eine Antwort abgegeben
 * wurde, für KEINEN der sieben Fragetypen ein Feld ausliefern, das die Lösung trägt. Kein
 * Spring-Kontext nötig (`SessionController` ist ein einfacher konstruierbarer Kotlin-Typ), also
 * ohne die Docker-Lücke lauffähig — gleiches Muster wie die übrigen reinen Domänentests. */
class SessionControllerTest {

    private val sessionService = mockk<SessionService>()
    private val attemptService = mockk<AttemptService>()
    private val controller = SessionController(sessionService, attemptService)
    private val mapper = JsonMapper.instance

    private val workspaceId = UUID.randomUUID()
    private val principal = TenantPrincipal(UUID.randomUUID(), workspaceId, "test@example.com")
    private val sessionId = UUID.randomUUID()

    private fun nextPayload(type: String, payloadJson: String): Any {
        every { sessionService.peekNext(workspaceId, sessionId) } returns
            SelectedItem(UUID.randomUUID(), UUID.randomUUID(), type, "stem", payloadJson, 0.5f)
        return controller.next(principal, sessionId)!!.payload
    }

    private fun asJson(payload: Any): String = mapper.writeValueAsString(payload)

    @Test
    fun `MC_SINGLE strips correct rationale and misconceptionCategory from every option`() {
        val json = asJson(
            nextPayload(
                "MC_SINGLE",
                """{"options":[{"id":"a","text":"A","correct":true,"rationale":"r1","misconceptionCategory":null},
                    |{"id":"b","text":"B","correct":false,"rationale":"r2","misconceptionCategory":"FACTUAL_GAP"}],"shuffle":true}""".trimMargin(),
            ),
        )
        assertFalse(json.contains("\"correct\""))
        assertFalse(json.contains("rationale"))
        assertFalse(json.contains("misconceptionCategory"))
        assertFalse(json.contains("FACTUAL_GAP"))
        assertTrue(json.contains("\"id\":\"a\""))
        assertTrue(json.contains("\"text\":\"B\""))
    }

    @Test
    fun `MC_MULTI strips answer-bearing option fields`() {
        val json = asJson(
            nextPayload(
                "MC_MULTI",
                """{"options":[{"id":"a","text":"A","correct":true,"rationale":"r1"},
                    |{"id":"b","text":"B","correct":true,"rationale":"r2"}],"shuffle":false}""".trimMargin(),
            ),
        )
        assertFalse(json.contains("\"correct\""))
        assertFalse(json.contains("rationale"))
    }

    @Test
    fun `TRUE_FALSE strips answer and rationale but keeps the statement`() {
        val json = asJson(nextPayload("TRUE_FALSE", """{"statement":"Die Erde ist rund.","answer":true,"rationale":"weil"}"""))
        assertFalse(json.contains("\"answer\""))
        assertFalse(json.contains("rationale"))
        assertTrue(json.contains("Die Erde ist rund."))
    }

    @Test
    fun `ORDERING strips correctOrder but keeps the elements`() {
        val json = asJson(
            nextPayload(
                "ORDERING",
                """{"elements":[{"id":"a","text":"A"},{"id":"b","text":"B"},{"id":"c","text":"C"}],"correctOrder":["a","b","c"],"partialCredit":true}""",
            ),
        )
        assertFalse(json.contains("correctOrder"))
        assertTrue(json.contains("\"id\":\"a\""))
    }

    @Test
    fun `MATCHING strips pairs but keeps left, right and distractors`() {
        val json = asJson(
            nextPayload(
                "MATCHING",
                """{"left":[{"id":"l1","text":"L1"},{"id":"l2","text":"L2"},{"id":"l3","text":"L3"}],
                    |"right":[{"id":"r1","text":"R1"}],"pairs":[{"leftId":"l1","rightId":"r1"}],"distractorsRight":[]}""".trimMargin(),
            ),
        )
        assertFalse(json.contains("\"pairs\""))
        assertTrue(json.contains("\"id\":\"l1\""))
        assertTrue(json.contains("\"id\":\"r1\""))
    }

    @Test
    fun `CLOZE strips accepted answers but keeps the template and blank count`() {
        val json = asJson(nextPayload("CLOZE", """{"template":"{{1}} ist {{2}}.","blanks":[{"accepted":["x"]},{"accepted":["y","z"]}]}"""))
        assertFalse(json.contains("\"x\""))
        assertFalse(json.contains("\"y\""))
        assertFalse(json.contains("\"z\""))
        assertTrue(json.contains("{{1}} ist {{2}}."))
        @Suppress("UNCHECKED_CAST")
        val blanks = (mapper.readValue(json, Map::class.java)["blanks"] as List<Map<String, Any>>)
        assertTrue(blanks.size == 2) // Blank-Anzahl bleibt erhalten (Client rendert danach die Eingabefelder).
    }

    @Test
    fun `SHORT_ANSWER strips referenceAnswer but keeps the rubric`() {
        val json = asJson(nextPayload("SHORT_ANSWER", """{"rubric":[{"criterion":"Nennt X","points":2}],"referenceAnswer":"Die geheime Antwort"}"""))
        assertFalse(json.contains("referenceAnswer"))
        assertFalse(json.contains("Die geheime Antwort"))
        assertTrue(json.contains("Nennt X"))
    }
}
