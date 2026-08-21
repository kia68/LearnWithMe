package de.optadata.odil.learnwithme.assessment.internal.grading

import de.optadata.odil.learnwithme.assessment.internal.domain.AttemptOutcome
import de.optadata.odil.learnwithme.shared.JsonMapper
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ResponseGraderTest {
    private val grader = ResponseGrader()
    private val mapper = JsonMapper.instance
    private fun tree(json: String) = mapper.readTree(json)

    @Test
    fun `MC_SINGLE correct choice scores 1 and returns its rationale`() {
        val payload = """{"options":[
            {"id":"a","text":"3NF","correct":true,"rationale":"Richtig."},
            {"id":"b","text":"2NF","correct":false,"rationale":"Falsch, zu schwach."}
        ]}"""
        val result = grader.grade("MC_SINGLE", payload, tree("""{"optionId":"a"}"""))
        result.score shouldBe 1f
        result.outcome shouldBe AttemptOutcome.CORRECT
        result.chosenOptionRationale shouldBe "Richtig."
    }

    @Test
    fun `MC_SINGLE wrong choice scores 0 and returns the chosen distractor's rationale`() {
        val payload = """{"options":[
            {"id":"a","text":"3NF","correct":true,"rationale":"Richtig."},
            {"id":"b","text":"2NF","correct":false,"rationale":"Falsch, zu schwach."}
        ]}"""
        val result = grader.grade("MC_SINGLE", payload, tree("""{"optionId":"b"}"""))
        result.score shouldBe 0f
        result.outcome shouldBe AttemptOutcome.INCORRECT
        result.chosenOptionRationale shouldBe "Falsch, zu schwach."
    }

    @Test
    fun `MC_SINGLE wrong choice returns the chosen distractor's misconceptionCategory`() {
        val payload = """{"options":[
            {"id":"a","text":"3NF","correct":true,"rationale":"Richtig."},
            {"id":"b","text":"2NF","correct":false,"rationale":"Falsch, zu schwach.","misconceptionCategory":"TERM_CONFUSION"}
        ]}"""
        val result = grader.grade("MC_SINGLE", payload, tree("""{"optionId":"b"}"""))
        result.chosenOptionMisconceptionCategory shouldBe "TERM_CONFUSION"
    }

    @Test
    fun `MC_SINGLE correct choice never reports a misconceptionCategory`() {
        val payload = """{"options":[
            {"id":"a","text":"3NF","correct":true,"rationale":"Richtig."},
            {"id":"b","text":"2NF","correct":false,"rationale":"Falsch, zu schwach.","misconceptionCategory":"TERM_CONFUSION"}
        ]}"""
        val result = grader.grade("MC_SINGLE", payload, tree("""{"optionId":"a"}"""))
        result.chosenOptionMisconceptionCategory shouldBe null
    }

    @Test
    fun `MC_MULTI gives partial credit and penalizes false positives`() {
        val payload = """{"options":[
            {"id":"a","text":"A","correct":true,"rationale":"r-a"},
            {"id":"b","text":"B","correct":true,"rationale":"r-b"},
            {"id":"c","text":"C","correct":false,"rationale":"r-c"}
        ]}"""
        // 1 von 2 korrekten gewählt, 1 falsche dazu -> (1-1)/2 = 0
        val result = grader.grade("MC_MULTI", payload, tree("""{"optionIds":["a","c"]}"""))
        result.score shouldBe 0f
        result.outcome shouldBe AttemptOutcome.INCORRECT
    }

    @Test
    fun `MC_MULTI with exactly the correct set scores 1`() {
        val payload = """{"options":[
            {"id":"a","text":"A","correct":true,"rationale":"r-a"},
            {"id":"b","text":"B","correct":true,"rationale":"r-b"},
            {"id":"c","text":"C","correct":false,"rationale":"r-c"}
        ]}"""
        val result = grader.grade("MC_MULTI", payload, tree("""{"optionIds":["a","b"]}"""))
        result.score shouldBe 1f
        result.outcome shouldBe AttemptOutcome.CORRECT
    }

    @Test
    fun `TRUE_FALSE correct answer scores 1`() {
        val payload = """{"statement":"3NF impliziert 2NF.","answer":true,"rationale":"Ja, per Definition."}"""
        val result = grader.grade("TRUE_FALSE", payload, tree("""{"answer":true}"""))
        result.score shouldBe 1f
        result.chosenOptionRationale shouldBe "Ja, per Definition."
    }

    @Test
    fun `ORDERING with correct sequence scores 1`() {
        val payload = """{"elements":[{"id":"1","text":"a"},{"id":"2","text":"b"},{"id":"3","text":"c"}],
            "correctOrder":["1","2","3"],"partialCredit":true}"""
        val result = grader.grade("ORDERING", payload, tree("""{"order":["1","2","3"]}"""))
        result.score shouldBe 1f
    }

    @Test
    fun `ORDERING with fully reversed sequence scores 0`() {
        val payload = """{"elements":[{"id":"1","text":"a"},{"id":"2","text":"b"},{"id":"3","text":"c"}],
            "correctOrder":["1","2","3"],"partialCredit":true}"""
        val result = grader.grade("ORDERING", payload, tree("""{"order":["3","2","1"]}"""))
        result.score shouldBe 0f
    }

    @Test
    fun `ORDERING with one adjacent swap gives partial credit between 0 and 1`() {
        val payload = """{"elements":[{"id":"1","text":"a"},{"id":"2","text":"b"},{"id":"3","text":"c"}],
            "correctOrder":["1","2","3"],"partialCredit":true}"""
        val result = grader.grade("ORDERING", payload, tree("""{"order":["2","1","3"]}"""))
        (result.score > 0f && result.score < 1f) shouldBe true
    }

    @Test
    fun `MATCHING scores the fraction of correctly matched pairs`() {
        val payload = """{"left":[{"id":"l1","text":"A"},{"id":"l2","text":"B"}],
            "right":[{"id":"r1","text":"X"},{"id":"r2","text":"Y"}],
            "pairs":[{"leftId":"l1","rightId":"r1"},{"leftId":"l2","rightId":"r2"}]}"""
        val result = grader.grade("MATCHING", payload, tree("""{"pairs":[{"leftId":"l1","rightId":"r1"},{"leftId":"l2","rightId":"r1"}]}"""))
        result.score shouldBe 0.5f
    }

    @Test
    fun `CLOZE normalizes whitespace and case before comparing`() {
        val payload = """{"template":"{{1}} kostet {{2}}.","blanks":[{"accepted":["Wert"]},{"accepted":["Preis","Kosten"]}]}"""
        val result = grader.grade("CLOZE", payload, tree("""{"answers":["  wert ","KOSTEN"]}"""))
        result.score shouldBe 1f
    }

    @Test
    fun `CLOZE with one wrong blank gives partial credit`() {
        val payload = """{"template":"{{1}} kostet {{2}}.","blanks":[{"accepted":["Wert"]},{"accepted":["Preis"]}]}"""
        val result = grader.grade("CLOZE", payload, tree("""{"answers":["Wert","falsch"]}"""))
        result.score shouldBe 0.5f
        result.outcome shouldBe AttemptOutcome.PARTIAL
    }
}
