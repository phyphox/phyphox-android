package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsEvent.NavigateBack
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.SettingsEvent.OpenWebpageFromResourceID
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.accessport.AccessPortViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.applanguage.AppLanguageViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.appuimode.UiModeViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.graphsize.GraphSizeViewmodelDelegate
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.proximitylock.ProximityLockViewmodelDelegate
import de.rwth_aachen.phyphox.utils.UIEventFlow
import de.rwth_aachen.phyphox.utils.asFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewmodelViewModel @Inject constructor(
    private val accessPortDelegate: AccessPortViewmodelDelegate,
    private val appLanguageDelegate: AppLanguageViewmodelDelegate,
    private val appUiModeDelegate: UiModeViewmodelDelegate,
    private val graphSizeDelegate: GraphSizeViewmodelDelegate,
    private val proximityLockDelegate: ProximityLockViewmodelDelegate,
) : ViewModel(),
    AccessPortViewmodelDelegate by accessPortDelegate,
    AppLanguageViewmodelDelegate by appLanguageDelegate,
    UiModeViewmodelDelegate by appUiModeDelegate,
    GraphSizeViewmodelDelegate by graphSizeDelegate,
    ProximityLockViewmodelDelegate by proximityLockDelegate {


    private val _uiEvent = UIEventFlow<SettingsEvent>()
    val uiEvent = _uiEvent.asFlow()

    init {
        start(viewModelScope)
    }

    override fun start(scope: CoroutineScope) {
        accessPortDelegate.start(scope)
        appLanguageDelegate.start(scope)
        appUiModeDelegate.start(scope)
        graphSizeDelegate.start(scope)
        proximityLockDelegate.start(scope)
    }

    private val uiModalFlow = combine(
        accessPortDelegate.portInputModal,
        appLanguageDelegate.languageSelectionModal,
    ) { modals: Array<SettingsSheetUiModel?> ->
        modals.firstOrNull { it != null }
    }
    val uiState = combine(
        accessPortDelegate.accessPortFlow,
        appLanguageDelegate.currentAppLanguageFlow,
        appUiModeDelegate.appUiModeFlow,
        graphSizeDelegate.graphSizeFlow,
        proximityLockDelegate.proximityLockFlow,
    ) { accessPort, currentLanguage, uiMode, graphSize, proximityLockEnabled ->
        UserSettings(
            currentLanguage = currentLanguage,
            graphSize = graphSize,
            appUiMode = uiMode,
            accessPort = accessPort,
            proximityLockEnabled = proximityLockEnabled,
        )
    }.combine(uiModalFlow) { userSettings, modal ->
        SettingsUiState(
            userSettings = userSettings,
            modal = modal,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
    )

    fun onActionEvent(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.OnGraphSizeChanged -> graphSizeDelegate.updateGraphSize(action.size)
                is SettingsAction.OnProximityLockChanged -> proximityLockDelegate.updateProximityLockStatus(action.enabled)
                is SettingsAction.OnUiModeItemSelected -> appUiModeDelegate.updateAppUiMode(action.appUiMode)
                is SettingsAction.OnAccessPortChanged -> accessPortDelegate.setAccessPort(action.newPort)
                is SettingsAction.OnAppLanguageChanged -> appLanguageDelegate.updateLanguage(action.newLanguageIdentifier)
                SettingsAction.OnAccessPortClicked -> accessPortDelegate.showAccessPortInputModal()
                SettingsAction.OnAppLanguageClicked -> appLanguageDelegate.showLanguagePickerModal()
                SettingsAction.OnModalDismissed -> dismissModal()
                SettingsAction.OnBackPressed -> sendEvent(NavigateBack)
                SettingsAction.OnLearnMoreAboutTranslationClicked -> sendEvent(
                    OpenWebpageFromResourceID(
                        R.string.settingsTranslation,
                    ),
                )
            }
        }
    }


    private fun sendEvent(event: SettingsEvent) = viewModelScope.launch {
        _uiEvent.emit(event)
    }

    override fun dismissModal() {
        accessPortDelegate.dismissModal()
        appLanguageDelegate.dismissModal()
    }
}
