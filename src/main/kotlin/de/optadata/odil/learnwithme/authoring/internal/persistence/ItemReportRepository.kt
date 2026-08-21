package de.optadata.odil.learnwithme.authoring.internal.persistence

import de.optadata.odil.learnwithme.authoring.internal.domain.ItemReport
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ItemReportRepository : JpaRepository<ItemReport, UUID>
