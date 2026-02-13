package de.rwth_aachen.phyphox.features.settings.presentation.compose.common.preferencecategoryheader

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import de.rwth_aachen.phyphox.ui.string.LoremIpsumStringUIModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel

class PreferenceCategoryHeaderPreviewProvider : PreviewParameterProvider<StringUIModel> {
    override val values: Sequence<StringUIModel>
        get() = sequenceOf(
            LoremIpsumStringUIModel(8),
        )
}



