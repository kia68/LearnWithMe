package de.optadata.odil.learnwithme.assessment.internal.selection

import java.util.UUID

data class SelectedItem(
    val itemId: UUID,
    val conceptId: UUID,
    val type: String,
    val stem: String,
    val payloadJson: String,
    val expectedSuccess: Float,
)
