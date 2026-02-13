package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.clickablepreferenceitem

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.rwth_aachen.phyphox.ui.string.LoremIpsumStringUIModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.utils.UiResourceState

class ClickablePreferenceItemPreviewProvider : PreviewParameterProvider<UiResourceState<StringUIModel>> {
    override val values: Sequence<UiResourceState<StringUIModel>>
        get() = sequenceOf(
            UiResourceState.Loading,
            UiResourceState.Success(LoremIpsumStringUIModel(8)),
        )
}
