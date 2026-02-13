package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.segmentedbuttonpreferenceitem

import de.rwth_aachen.phyphox.ui.string.StringUIModel

data class SegmentedButtonUiModel<out T>(
    val item: T,
    val text: StringUIModel,
    val isSelected: Boolean = false,
)
