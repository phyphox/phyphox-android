package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel

import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage
import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import de.rwth_aachen.phyphox.features.settings.domain.model.errors.AccessPortOutOfRange
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.seekbarpreferenceitem.SeekBarConfig
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.segmentedbuttonpreferenceitem.SegmentedButtonUiModel
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage.LanguageUiModel
import de.rwth_aachen.phyphox.ui.string.ResourceStringUIModel
import de.rwth_aachen.phyphox.ui.string.StringUIModel
import de.rwth_aachen.phyphox.ui.string.toStringUIModel
import de.rwth_aachen.phyphox.utils.UiResourceState
import java.util.Locale
import javax.inject.Inject

internal class UiBuilder @Inject constructor(

) {

    //region - App Language
    fun getSortedLanguageModels(supportedLanguages: List<AppLanguage>): List<LanguageUiModel> {
        val systemDefaultModel = buildLanguageUiModel(AppLanguage.SYSTEM_DEFAULT)
        val otherLanguageModels = supportedLanguages
            .filter { it != AppLanguage.SYSTEM_DEFAULT }
            .map {
                Pair(
                    it.identifier,
                    Locale.forLanguageTag(it.identifier),
                )
            }
            .sortedBy { it.second.displayName }
            .map { localeToLanguageUiModel(it.first, it.second) }
        return listOf(systemDefaultModel) + otherLanguageModels
    }

    fun buildLanguageUiModel(languageResource: UiResourceState<AppLanguage>): UiResourceState<LanguageUiModel> {
        return when (languageResource) {
            UiResourceState.Loading -> UiResourceState.Loading
            is UiResourceState.Success<AppLanguage> -> UiResourceState.Success(
                buildLanguageUiModel(languageResource.data),
            )
        }
    }

    fun buildLanguageUiModel(appLanguage: AppLanguage): LanguageUiModel {
        return if (appLanguage.identifier == AppLanguage.SYSTEM_DEFAULT_IDENTIFIER) {
            LanguageUiModel(
                identifier = AppLanguage.SYSTEM_DEFAULT_IDENTIFIER,
                displayName = R.string.settingsDefault.toStringUIModel(),
            )
        } else {
            val locale = Locale.forLanguageTag(appLanguage.identifier)
            localeToLanguageUiModel(appLanguage.identifier, locale)
        }
    }

    fun localeToLanguageUiModel(identifier: String, locale: Locale): LanguageUiModel {
        return LanguageUiModel(
            identifier = identifier,
            displayName = locale.getDisplayName(locale).toStringUIModel(),
            localDisplayName = locale.displayName.toStringUIModel(),
            displayCountry = locale.getDisplayCountry(locale).toStringUIModel(),
        )
    }
    //endregion

    //region - AppUiMode
    fun buildAppUiModeResource(
        current: UiResourceState<AppUiMode>,
        modes: UiResourceState<List<AppUiMode>>,
    ): UiResourceState<List<SegmentedButtonUiModel<AppUiMode>>> {
        return if (current is UiResourceState.Success && modes is UiResourceState.Success) {
            UiResourceState.Success(
                modes.data.map { mode ->
                    SegmentedButtonUiModel(
                        item = mode,
                        isSelected = current.data == mode,
                        text = getSupportedUiModeText(mode),
                    )
                },
            )
        } else {
            UiResourceState.Loading
        }
    }

    private fun getSupportedUiModeText(appUiMode: AppUiMode): StringUIModel {
        return when (appUiMode) {
            AppUiMode.SYSTEM -> ResourceStringUIModel(R.string.settings_mode_dark_system)
            AppUiMode.LIGHT -> ResourceStringUIModel(R.string.settings_mode_no_dark)
            AppUiMode.DARK -> ResourceStringUIModel(R.string.settings_mode_dark)
        }
    }

    //endregion

    //region - AccessPort
    fun buildAccessPortUiModel(
        portResource: UiResourceState<Int>,
    ): UiResourceState<StringUIModel> {
        return if (portResource is UiResourceState.Success) {
            UiResourceState.Success(
                portResource.data.toString().toStringUIModel(),
            )
        } else {
            UiResourceState.Loading
        }
    }
    //endregion

    //region - Graph Size
    fun buildGraphSizeUiModel(
        currentGraphSizeFlow: UiResourceState<Float>,
        graphSizeRangeFlow: UiResourceState<ClosedFloatingPointRange<Float>>,
    ): UiResourceState<SeekBarConfig> {
        return when {
            currentGraphSizeFlow is UiResourceState.Success && graphSizeRangeFlow is UiResourceState.Success -> {
                UiResourceState.Success(
                    SeekBarConfig(
                        currentSize = currentGraphSizeFlow.data,
                        range = graphSizeRangeFlow.data.start..graphSizeRangeFlow.data.endInclusive,
                    ),
                )
            }

            else -> UiResourceState.Loading
        }
    }

    fun getErrorMessage(error: Throwable): StringUIModel {
        return when (error) {
            is AccessPortOutOfRange -> "${error.intRange.first} - ${error.intRange.last}".toStringUIModel()
            else -> "Unknown error".toStringUIModel()
        }
    }


    //endregion
}
