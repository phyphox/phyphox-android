package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel

import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.seekbarpreferenceitem.SeekBarConfig
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.segmentedbuttonpreferenceitem.SegmentedButtonUiModel
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage.LanguageUiModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.utils.UiResourceState

data class SettingsUiState(
    val currentLanguage: UiResourceState<LanguageUiModel> = UiResourceState.Loading,
    val graphSize: UiResourceState<SeekBarConfig> = UiResourceState.Loading,
    val appUiMode: UiResourceState<List<SegmentedButtonUiModel<AppUiMode>>> = UiResourceState.Loading,
    val accessPort: UiResourceState<StringUIModel> = UiResourceState.Loading,
    val proximityLockEnabled: UiResourceState<Boolean> = UiResourceState.Loading,
    val modal: SettingsSheetUiModel? = null,
) {
    constructor(
        userSettings: UserSettings = UserSettings(),
        modal: SettingsSheetUiModel? = null,
    ) : this(
        currentLanguage = userSettings.currentLanguage,
        graphSize = userSettings.graphSize,
        appUiMode = userSettings.appUiMode,
        accessPort = userSettings.accessPort,
        proximityLockEnabled = userSettings.proximityLockEnabled,
        modal = modal,
    )
}

data class UserSettings(
    val currentLanguage: UiResourceState<LanguageUiModel> = UiResourceState.Loading,
    val graphSize: UiResourceState<SeekBarConfig> = UiResourceState.Loading,
    val appUiMode: UiResourceState<List<SegmentedButtonUiModel<AppUiMode>>> = UiResourceState.Loading,
    val accessPort: UiResourceState<StringUIModel> = UiResourceState.Loading,
    val proximityLockEnabled: UiResourceState<Boolean> = UiResourceState.Loading,
    //    val dynamicTheme: ResourceState<Boolean> = ResourceState.Loading,
)
