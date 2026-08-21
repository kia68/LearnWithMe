package de.optadata.odil.learnwithme.ai.internal.service

import de.optadata.odil.learnwithme.ai.internal.domain.CredentialStatus
import de.optadata.odil.learnwithme.ai.internal.persistence.AiCredentialRepository
import de.optadata.odil.learnwithme.ai.internal.persistence.LlmUsageRepository
import de.optadata.odil.learnwithme.ai.internal.web.dto.QuotaInfo
import de.optadata.odil.learnwithme.identity.IdentityApi
import de.optadata.odil.learnwithme.shared.web.QuotaExceededException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * A6: hartes Monatslimit für Nutzer ohne eigenen API-Key. Diese Klasse liefert die
 * Quota-Prüfung/-Auskunft bereits vollständig; der Aufruf vor einem tatsächlichen
 * LLM-Call folgt erst mit dem `LlmGateway` in M1 (ADR-004) — vorher gibt es schlicht
 * keine LLM-Aufrufe, die begrenzt werden müssten.
 */
@Service
class QuotaService(
    private val identityApi: IdentityApi,
    private val usageRepository: LlmUsageRepository,
    private val credentialRepository: AiCredentialRepository,
    @Value("\${learnwithme.limits.free-monthly-cost-micros}") private val freeMonthlyCostMicros: Long,
) {

    fun quotaInfo(workspaceId: UUID): QuotaInfo {
        val plan = identityApi.getPlan(workspaceId)
        if (plan != "FREE" || hasVerifiedByok(workspaceId)) {
            return QuotaInfo(plan = plan, limitMicros = null, usedMicros = 0, exceeded = false)
        }
        val used = usageRepository.sumCostMicrosSince(workspaceId, startOfCurrentMonth())
        return QuotaInfo(
            plan = plan,
            limitMicros = freeMonthlyCostMicros,
            usedMicros = used,
            exceeded = used >= freeMonthlyCostMicros,
        )
    }

    /** Wirft [QuotaExceededException], wenn der Workspace sein Plattform-Key-Freikontingent
     * ausgeschöpft hat und keinen eigenen (verifizierten) Key hinterlegt hat. */
    fun assertWithinFreeQuota(workspaceId: UUID) {
        val info = quotaInfo(workspaceId)
        if (info.exceeded) {
            throw QuotaExceededException(
                "Monatliches Gratis-Kontingent ausgeschöpft. Bitte eigenen API-Key hinterlegen " +
                    "(POST /api/v1/ai/credentials) oder auf einen bezahlten Plan upgraden.",
            )
        }
    }

    private fun hasVerifiedByok(workspaceId: UUID): Boolean =
        credentialRepository.existsByWorkspaceIdAndStatus(workspaceId, CredentialStatus.VERIFIED)

    private fun startOfCurrentMonth(): Instant =
        Instant.now().atZone(ZoneOffset.UTC).with(TemporalAdjusters.firstDayOfMonth())
            .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
}
