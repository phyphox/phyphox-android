package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage

import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsSheetUiModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel

data class AppLanguageSheetUiModel(
    val title: StringUIModel,
    val currentSelection: AppLanguage,
    val availableLocales: List<LanguageUiModel>,
) : SettingsSheetUiModel


