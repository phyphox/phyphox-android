package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage

import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.SettingsViewmodelDelegate
import de.rwth_aachen.phyphox.utils.UiResourceState
import kotlinx.coroutines.flow.Flow

interface AppLanguageViewmodelDelegate : SettingsViewmodelDelegate {

    val currentAppLanguageFlow: Flow<UiResourceState<LanguageUiModel>>

    val languageSelectionModal: Flow<AppLanguageSheetUiModel?>

    suspend fun showLanguagePickerModal()

    suspend fun updateLanguage(identifier: String)
    fun dismissModal()
}
