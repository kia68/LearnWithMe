package de.optadata.odil.learnwithme.authoring.internal.domain

import de.optadata.odil.learnwithme.shared.JsonMapper

/** Type-getriebene De-/Serialisierung statt Jackson-Polymorphie — siehe [ItemPayload]-Kommentar. */
object PayloadCodec {
    private val mapper = JsonMapper.instance

    fun serialize(payload: ItemPayload): String = mapper.writeValueAsString(payload)

    fun deserialize(type: ItemType, json: String): ItemPayload = when (type) {
        ItemType.MC_SINGLE -> mapper.readValue(json, McSinglePayload::class.java)
        ItemType.MC_MULTI -> mapper.readValue(json, McMultiPayload::class.java)
        ItemType.TRUE_FALSE -> mapper.readValue(json, TrueFalsePayload::class.java)
        ItemType.ORDERING -> mapper.readValue(json, OrderingPayload::class.java)
        ItemType.MATCHING -> mapper.readValue(json, MatchingPayload::class.java)
        ItemType.CLOZE -> mapper.readValue(json, ClozePayload::class.java)
        ItemType.SHORT_ANSWER -> mapper.readValue(json, ShortAnswerPayload::class.java)
    }

    fun targetClass(type: ItemType): Class<out ItemPayload> = when (type) {
        ItemType.MC_SINGLE -> McSinglePayload::class.java
        ItemType.MC_MULTI -> McMultiPayload::class.java
        ItemType.TRUE_FALSE -> TrueFalsePayload::class.java
        ItemType.ORDERING -> OrderingPayload::class.java
        ItemType.MATCHING -> MatchingPayload::class.java
        ItemType.CLOZE -> ClozePayload::class.java
        ItemType.SHORT_ANSWER -> ShortAnswerPayload::class.java
    }
}
