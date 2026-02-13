package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.switchpreferenceitem

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.rwth_aachen.phyphox.utils.UiResourceState

class SwitchPreferenceItemPreviewProvider : PreviewParameterProvider<UiResourceState<Boolean>> {
    override val values: Sequence<UiResourceState<Boolean>>
        get() = sequenceOf(
            UiResourceState.Loading,
            UiResourceState.Success(true),
            UiResourceState.Success(false),
        )
}
