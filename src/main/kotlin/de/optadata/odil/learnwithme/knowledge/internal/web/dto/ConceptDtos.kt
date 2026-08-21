package de.optadata.odil.learnwithme.knowledge.internal.web.dto

import java.util.UUID

data class ConceptResponse(val id: UUID, val name: String, val summary: String, val frequency: Int)
