package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.graphsize

import de.rwth_aachen.phyphox.features.settings.presentation.compose.common.seekbarpreferenceitem.SeekBarConfig
import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.SettingsViewmodelDelegate
import de.rwth_aachen.phyphox.utils.UiResourceState
import kotlinx.coroutines.flow.Flow

interface GraphSizeViewmodelDelegate  : SettingsViewmodelDelegate{
    val graphSizeFlow: Flow<UiResourceState<SeekBarConfig>>
    suspend fun updateGraphSize(size: Float)
}
