package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage

import de.rwth_aachen.phyphox.ui.string.StringUIModel

data class LanguageUiModel(
    val identifier: String,
    val displayName: StringUIModel,
    val localDisplayName: StringUIModel? = null,
    val displayCountry: StringUIModel? = null,
)
