package de.optadata.odil.learnwithme.analytics.internal.persistence

import de.optadata.odil.learnwithme.analytics.internal.domain.ErrorEvent
import org.springframework.data.jpa.repository.JpaRepository

interface ErrorEventRepository : JpaRepository<ErrorEvent, Long>
