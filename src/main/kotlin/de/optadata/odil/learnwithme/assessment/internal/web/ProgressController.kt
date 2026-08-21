package de.optadata.odil.learnwithme.assessment.internal.web

import de.optadata.odil.learnwithme.adaptivity.ConceptProgressView
import de.optadata.odil.learnwithme.assessment.internal.service.ProgressService
import de.optadata.odil.learnwithme.assessment.internal.web.dto.ConceptProgressResponse
import de.optadata.odil.learnwithme.assessment.internal.web.dto.ProgressOverviewResponse
import de.optadata.odil.learnwithme.shared.TenantPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** D7: Fortschritt pro Konzept, D5: fällige Wiederholungen. `misconceptions` (Epic E) ist
 * bewusst nicht hier — keine Epic-D-Story. */
@RestController
@RequestMapping("/api/v1/progress")
class ProgressController(private val progressService: ProgressService) {

    @GetMapping("/overview")
    fun overview(@AuthenticationPrincipal principal: TenantPrincipal): ProgressOverviewResponse {
        val overview = progressService.overview(principal.workspaceId, principal.userId)
        return ProgressOverviewResponse(overview.conceptCount, overview.averageMastery, overview.dueCount)
    }

    @GetMapping("/concepts")
    fun concepts(@AuthenticationPrincipal principal: TenantPrincipal, @RequestParam sourceId: UUID): List<ConceptProgressResponse> =
        progressService.forSource(principal.workspaceId, principal.userId, sourceId).map { it.toResponse() }

    @GetMapping("/due")
    fun due(@AuthenticationPrincipal principal: TenantPrincipal): List<ConceptProgressResponse> =
        progressService.due(principal.workspaceId, principal.userId).map { it.toResponse() }

    private fun ConceptProgressView.toResponse() = ConceptProgressResponse(conceptId, theta, mastery, state, reps, lapses, dueAt)
}
