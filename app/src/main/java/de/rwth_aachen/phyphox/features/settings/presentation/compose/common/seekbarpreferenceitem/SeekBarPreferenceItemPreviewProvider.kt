package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.seekbarpreferenceitem

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.rwth_aachen.phyphox.utils.UiResourceState

class SeekBarPreferenceItemPreviewProvider : PreviewParameterProvider<UiResourceState<SeekBarConfig>> {
    override val values: Sequence<UiResourceState<SeekBarConfig>>
        get() = sequenceOf(
            UiResourceState.Loading,
            UiResourceState.Success(
                SeekBarConfig(
                    currentSize = 1f,
                    minSize = 0f,
                    maxSize = 3f,
                ),
            ),
        )
}
