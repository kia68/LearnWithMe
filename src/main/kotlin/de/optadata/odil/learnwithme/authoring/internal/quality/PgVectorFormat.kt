package de.optadata.odil.learnwithme.authoring.internal.quality

/** pgvector-Textliteral-Format (`"[0.1,0.2,...]"`) für native Queries — siehe `ItemRepository`. */
object PgVectorFormat {
    fun toLiteral(vector: FloatArray): String = vector.joinToString(",", prefix = "[", postfix = "]")
}
