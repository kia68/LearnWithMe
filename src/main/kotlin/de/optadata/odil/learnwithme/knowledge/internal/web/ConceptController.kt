package de.optadata.odil.learnwithme.knowledge.internal.web

import de.optadata.odil.learnwithme.knowledge.internal.domain.Concept
import de.optadata.odil.learnwithme.knowledge.internal.service.ConceptQueryService
import de.optadata.odil.learnwithme.knowledge.internal.web.dto.ConceptResponse
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** B8: extrahierte Kernkonzepte eines Dokuments. */
@RestController
@RequestMapping("/api/v1/sources/{sourceId}/concepts")
class ConceptController(private val conceptQueryService: ConceptQueryService) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: TenantPrincipal, @PathVariable sourceId: UUID): List<ConceptResponse> =
        conceptQueryService.listForSource(principal.workspaceId, sourceId).map { it.toResponse() }

    private fun Concept.toResponse() = ConceptResponse(id = id, name = name, summary = summary, frequency = frequency)
}
