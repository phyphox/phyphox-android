package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.accessport

import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsSheetUiModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel

data class AccessPortUiState(
    val currentAccessPort: StringUIModel,
    val accessPortRange: IntRange,
)
