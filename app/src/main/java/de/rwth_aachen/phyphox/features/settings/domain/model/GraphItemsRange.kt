package de.rwth_aachen.phyphox.features.settings.domain.model

data class GraphItemsRange(
    val labelSizeRange: ClosedFloatingPointRange<Float> = 0.5f..2.5f,
    val textSizeRange: ClosedFloatingPointRange<Float> = 0.5f..2.5f,
    val lineWidthRange: ClosedFloatingPointRange<Float> = 0.5f..2.5f,
    val borderWidthRange: ClosedFloatingPointRange<Float> = 0.5f..2.5f,
)
