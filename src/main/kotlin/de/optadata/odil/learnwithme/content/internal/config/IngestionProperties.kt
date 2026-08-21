package de.optadata.odil.learnwithme.content.internal.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class IngestionProperties(
    @Value("\${learnwithme.ingestion.max-pages}") val maxPages: Int,
    @Value("\${learnwithme.ingestion.chunk-target-tokens}") val chunkTargetTokens: Int,
    @Value("\${learnwithme.ingestion.chunk-overlap-tokens}") val chunkOverlapTokens: Int,
    @Value("\${learnwithme.ingestion.ocr-text-density-threshold}") val ocrTextDensityThreshold: Int,
)
