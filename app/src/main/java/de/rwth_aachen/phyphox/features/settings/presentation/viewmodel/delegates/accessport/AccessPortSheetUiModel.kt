package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.accessport

import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsSheetUiModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel

data class AccessPortSheetUiModel(
    val currentPort: StringUIModel,
    val range: IntRange,
    val error: StringUIModel? = null,
) : SettingsSheetUiModel
