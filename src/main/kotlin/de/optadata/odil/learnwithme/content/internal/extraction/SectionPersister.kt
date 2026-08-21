package de.optadata.odil.learnwithme.content.internal.extraction

import de.optadata.odil.learnwithme.content.internal.domain.Section
import de.optadata.odil.learnwithme.content.internal.persistence.SectionRepository
import org.springframework.stereotype.Component
import java.util.UUID

/** Baut aus der flachen [BuiltSection]-Liste einen Baum (B6): Parent ist die nächste
 * vorherige Section mit kleinerem Level (Level-Stack). Gibt die gespeicherten Sections in
 * derselben Reihenfolge wie [built] zurück, damit Aufrufer sie 1:1 zuordnen können. */
@Component
class SectionPersister(private val sectionRepository: SectionRepository) {

    fun persist(sourceId: UUID, built: List<BuiltSection>): List<Section> {
        val stack = ArrayDeque<Section>()
        val saved = mutableListOf<Section>()
        for (b in built) {
            while (stack.isNotEmpty() && stack.last().level >= b.level) stack.removeLast()
            val section = sectionRepository.save(
                Section(sourceId = sourceId, parentId = stack.lastOrNull()?.id, ordinal = b.ordinal, level = b.level, title = b.title),
            )
            stack.addLast(section)
            saved += section
        }
        return saved
    }
}
