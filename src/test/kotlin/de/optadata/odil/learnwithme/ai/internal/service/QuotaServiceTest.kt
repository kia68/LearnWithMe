package de.optadata.odil.learnwithme.ai.internal.service

import de.optadata.odil.learnwithme.ai.internal.domain.CredentialStatus
import de.optadata.odil.learnwithme.ai.internal.persistence.AiCredentialRepository
import de.optadata.odil.learnwithme.ai.internal.persistence.LlmUsageRepository
import de.optadata.odil.learnwithme.identity.IdentityApi
import de.optadata.odil.learnwithme.shared.web.QuotaExceededException
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith

class QuotaServiceTest {

    private val identityApi = mockk<IdentityApi>()
    private val usageRepository = mockk<LlmUsageRepository>()
    private val credentialRepository = mockk<AiCredentialRepository>()
    private val workspaceId = UUID.randomUUID()
    private val limitMicros = 2_000_000L

    private val quotaService = QuotaService(identityApi, usageRepository, credentialRepository, limitMicros)

    @Test
    fun `paid plan is never limited regardless of spend`() {
        every { identityApi.getPlan(workspaceId) } returns "PRO"

        val info = quotaService.quotaInfo(workspaceId)

        info.limitMicros shouldBe null
        info.exceeded shouldBe false
    }

    @Test
    fun `free plan with verified BYOK credential bypasses the platform quota`() {
        every { identityApi.getPlan(workspaceId) } returns "FREE"
        every { credentialRepository.existsByWorkspaceIdAndStatus(workspaceId, CredentialStatus.VERIFIED) } returns true

        val info = quotaService.quotaInfo(workspaceId)

        info.exceeded shouldBe false
    }

    @Test
    fun `free plan under the limit is not exceeded`() {
        every { identityApi.getPlan(workspaceId) } returns "FREE"
        every { credentialRepository.existsByWorkspaceIdAndStatus(workspaceId, CredentialStatus.VERIFIED) } returns false
        every { usageRepository.sumCostMicrosSince(workspaceId, any()) } returns 1_000_000L

        val info = quotaService.quotaInfo(workspaceId)

        info.limitMicros shouldBe limitMicros
        info.usedMicros shouldBe 1_000_000L
        info.exceeded shouldBe false
    }

    @Test
    fun `free plan at or above the limit is exceeded and asserting throws`() {
        every { identityApi.getPlan(workspaceId) } returns "FREE"
        every { credentialRepository.existsByWorkspaceIdAndStatus(workspaceId, CredentialStatus.VERIFIED) } returns false
        every { usageRepository.sumCostMicrosSince(workspaceId, any()) } returns limitMicros

        quotaService.quotaInfo(workspaceId).exceeded shouldBe true
        assertFailsWith<QuotaExceededException> { quotaService.assertWithinFreeQuota(workspaceId) }
    }
}
