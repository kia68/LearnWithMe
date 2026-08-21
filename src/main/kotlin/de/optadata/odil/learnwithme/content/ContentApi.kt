package de.optadata.odil.learnwithme.content

import java.util.UUID

data class ChunkView(val id: UUID, val ordinal: Int, val text: String)

/** Öffentlicher Port des `content`-Moduls, u.a. für `knowledge` (Konzeptextraktion, B8). */
interface ContentApi {
    /** Wirft `NotFoundException`, wenn die Source nicht existiert oder nicht zu [workspaceId] gehört. */
    fun assertOwned(sourceId: UUID, workspaceId: UUID)

    fun listChunks(sourceId: UUID): List<ChunkView>
}
