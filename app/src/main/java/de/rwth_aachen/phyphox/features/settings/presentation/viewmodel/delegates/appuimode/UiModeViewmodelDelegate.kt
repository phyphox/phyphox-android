package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.appuimode

import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.segmentedbuttonpreferenceitem.SegmentedButtonUiModel
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.SettingsViewmodelDelegate
import de.rwth_aachen.phyphox.utils.UiResourceState
import kotlinx.coroutines.flow.Flow

interface UiModeViewmodelDelegate : SettingsViewmodelDelegate {
    val appUiModeFlow: Flow<UiResourceState<List<SegmentedButtonUiModel<AppUiMode>>>>

    suspend fun updateAppUiMode(appUiMode: AppUiMode)
}
