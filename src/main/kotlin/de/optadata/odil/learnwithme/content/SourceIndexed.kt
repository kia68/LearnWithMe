package de.optadata.odil.learnwithme.content

import java.util.UUID

/**
 * Gefeuert, sobald `chunks` für eine Source vollständig persistiert sind (Status → `READY`).
 * `knowledge` hört darauf, um Kernkonzepte zu extrahieren (B8) — asynchron und nach dem
 * Zeitpunkt, an dem der Nutzer schon lernen könnte (N2), damit Konzeptextraktion nicht
 * auf dem kritischen Ingestion-Pfad liegt.
 *
 * Liegt bewusst im Wurzelpaket statt in einem `events`-Unterpaket (wie in docs/PLAN.md §6.3
 * skizziert): Spring Modulith behandelt standardmäßig nur das Wurzelpaket als öffentliche API
 * (Named Interface) — ein `events`-Unterpaket bräuchte zusätzlich eine `@NamedInterface`-Markierung,
 * die in einem reinen Kotlin-Sourceset ohne `package-info.java` nicht ohne Weiteres verifizierbar
 * war (siehe `ModularityTest`, gleiche Kategorie Entscheidung wie bei `ApiException`).
 */
data class SourceIndexed(val sourceId: UUID, val workspaceId: UUID)
