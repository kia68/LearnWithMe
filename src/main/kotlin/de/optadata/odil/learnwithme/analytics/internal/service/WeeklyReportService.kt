package de.optadata.odil.learnwithme.analytics.internal.service

import de.optadata.odil.learnwithme.adaptivity.AdaptivityApi
import de.optadata.odil.learnwithme.knowledge.KnowledgeApi
import org.springframework.stereotype.Service
import java.util.UUID

data class WeeklyReportGap(val conceptId: UUID, val conceptName: String, val mastery: Float)

data class WeeklyReport(val topGaps: List<WeeklyReportGap>, val recommendedFocus: WeeklyReportGap?)

/**
 * E5, In-App-Teil. Abweichung von PLAN.md §6.3 (analytics ──▶ shared, ai): `analytics` braucht
 * hier zusätzlich `adaptivity`(API) für die Beherrschungsgrade und `knowledge`(API) für die
 * Konzeptnamen — von `ApplicationModules.verify()` erlaubt (keine `internal`-Zugriffe, keine
 * Zyklen), analog zur bereits in Epic D dokumentierten Abweichung von `assessment`.
 *
 * Bewusst OHNE E-Mail-Versand (keine SMTP-Infrastruktur in dieser Codebase) und OHNE "Trend"
 * (bräuchte eine History-Tabelle über Mastery-Werte, die nirgendwo geführt wird — derselbe
 * bereits in Epic D dokumentierte Verlauf-über-Zeit-Gap). Nur der aktuelle Stand.
 */
@Service
class WeeklyReportService(
    private val adaptivityApi: AdaptivityApi,
    private val knowledgeApi: KnowledgeApi,
) {
    fun weeklyReport(workspaceId: UUID, userId: UUID): WeeklyReport {
        val gaps = adaptivityApi.listAllProgress(workspaceId, userId)
            .sortedBy { it.mastery }
            .take(3)
            .map { progress ->
                val conceptName = runCatching { knowledgeApi.getConcept(progress.conceptId).name }.getOrDefault("Unbekanntes Konzept")
                WeeklyReportGap(progress.conceptId, conceptName, progress.mastery)
            }
        return WeeklyReport(gaps, gaps.firstOrNull())
    }
}
