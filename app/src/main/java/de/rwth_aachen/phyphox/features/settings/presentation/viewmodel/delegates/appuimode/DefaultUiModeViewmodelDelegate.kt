package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.appuimode

import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.segmentedbuttonpreferenceitem.SegmentedButtonUiModel
import de.rwth_aachen.phyphox.utils.UiResourceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

internal class DefaultUiModeViewmodelDelegate @Inject constructor() : UiModeViewmodelDelegate {

    override val appUiModeFlow: Flow<UiResourceState<List<SegmentedButtonUiModel<AppUiMode>>>> =
        flowOf(UiResourceState.Loading)


    override fun start(scope: CoroutineScope) {}

    override suspend fun updateAppUiMode(appUiMode: AppUiMode) {}
}

