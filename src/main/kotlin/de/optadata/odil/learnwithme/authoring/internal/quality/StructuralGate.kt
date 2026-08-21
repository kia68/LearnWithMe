package de.optadata.odil.learnwithme.authoring.internal.quality

import de.optadata.odil.learnwithme.authoring.internal.domain.ItemPayload
import de.optadata.odil.learnwithme.authoring.internal.domain.ValidationIssue
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** C3: deterministische Strukturvalidatoren (kein LLM). */
@Component
class StructuralGate(
    @Value("\${learnwithme.quality.max-option-length-variance-ratio}") private val maxOptionLengthVarianceRatio: Double,
) {
    fun check(payload: ItemPayload): List<ValidationIssue> = payload.validate(maxOptionLengthVarianceRatio)
}
