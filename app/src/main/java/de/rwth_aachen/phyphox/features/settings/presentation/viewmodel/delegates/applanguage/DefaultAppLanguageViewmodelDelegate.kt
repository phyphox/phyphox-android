package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage

import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage
import de.rwth_aachen.phyphox.features.settings.domain.usecase.language.GetSupportedLanguagesUseCase
import de.rwth_aachen.phyphox.features.settings.domain.usecase.language.ObserveCurrentAppLanguageUseCase
import de.rwth_aachen.phyphox.features.settings.domain.usecase.language.UpdateAppLanguageUseCase
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.UiBuilder
import de.rwth_aachen.phyphox.ui.string.toStringUIModel
import de.rwth_aachen.phyphox.utils.UiResourceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

internal class DefaultAppLanguageViewmodelDelegate @Inject constructor(
    private val observeCurrentAppLanguage: ObserveCurrentAppLanguageUseCase,
    private val getSupportedLanguages: GetSupportedLanguagesUseCase,
    private val updateAppLanguage: UpdateAppLanguageUseCase,
    private val uiBuilder: UiBuilder,
) : AppLanguageViewmodelDelegate {
    private val currentLanguageFlow = MutableStateFlow<UiResourceState<AppLanguage>>(UiResourceState.Loading)
    override val currentAppLanguageFlow: Flow<UiResourceState<LanguageUiModel>> =
        currentLanguageFlow.map { appLanguage ->
            uiBuilder.buildLanguageUiModel(appLanguage)
        }
    private val inputModalStaFlow = MutableStateFlow<AppLanguageSheetUiModel?>(null)

    override val languageSelectionModal: Flow<AppLanguageSheetUiModel?> = inputModalStaFlow

    override fun start(scope: CoroutineScope) {
        observeCurrentAppLanguage().onStart {
            currentLanguageFlow.value = UiResourceState.Loading
        }.onEach {
            currentLanguageFlow.value = UiResourceState.Success(it)
        }.launchIn(scope)
    }

    override suspend fun showLanguagePickerModal() {
        val currentLocale = when (val temp = currentLanguageFlow.value) {
            UiResourceState.Loading -> null
            is UiResourceState.Success<AppLanguage> -> temp.data
        }
        val supportedLanguages = uiBuilder.getSortedLanguageModels(getSupportedLanguages())
        inputModalStaFlow.value = AppLanguageSheetUiModel(
            title = R.string.settingsLanguage.toStringUIModel(),
            currentSelection = currentLocale ?: AppLanguage.SYSTEM_DEFAULT,
            availableLocales = supportedLanguages,
        )
    }

    override suspend fun updateLanguage(identifier: String) {
        updateAppLanguage(identifier)
        dismissModal()
    }

    override fun dismissModal() {
        inputModalStaFlow.value = null
    }
}
