package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.seekbarpreferenceitem

data class SeekBarConfig(
    val currentSize: Float,
    val range: ClosedFloatingPointRange<Float>,
) {
    constructor(
        currentSize: Float,
        minSize: Float,
        maxSize: Float,
    ) : this(
        currentSize = currentSize,
        range = minSize..maxSize,
    )
}
