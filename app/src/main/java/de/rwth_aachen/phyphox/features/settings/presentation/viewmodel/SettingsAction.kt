package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel

import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.segmentedbuttonpreferenceitem.SegmentedButtonUiModel

sealed interface SettingsAction {

    data class OnUiModeItemSelected(val appUiMode: AppUiMode) : SettingsAction
    data class OnGraphSizeChanged(val size: Float) : SettingsAction
    data class OnProximityLockChanged(val enabled: Boolean) : SettingsAction
    data class OnAccessPortChanged(val newPort: String) : SettingsAction
    data class OnAppLanguageChanged(val newLanguageIdentifier: String) : SettingsAction
    data object OnModalDismissed : SettingsAction
    data object OnAppLanguageClicked : SettingsAction
    data object OnLearnMoreAboutTranslationClicked : SettingsAction
    data object OnAccessPortClicked : SettingsAction
    data object OnBackPressed : SettingsAction
}
