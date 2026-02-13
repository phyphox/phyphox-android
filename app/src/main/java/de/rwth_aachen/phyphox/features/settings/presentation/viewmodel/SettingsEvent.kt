package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel

import androidx.annotation.StringRes

sealed interface SettingsEvent {
    data class OpenWebpage(val url: String) : SettingsEvent
    data class OpenWebpageFromResourceID(@param:StringRes val urlResourceId: Int) : SettingsEvent

    data object NavigateBack : SettingsEvent
}
