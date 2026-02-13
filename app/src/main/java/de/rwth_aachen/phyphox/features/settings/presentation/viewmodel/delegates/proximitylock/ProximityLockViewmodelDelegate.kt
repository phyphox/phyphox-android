package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.proximitylock

import de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.SettingsViewmodelDelegate
import de.rwth_aachen.phyphox.utils.UiResourceState
import kotlinx.coroutines.flow.Flow

interface ProximityLockViewmodelDelegate: SettingsViewmodelDelegate {
    val proximityLockFlow: Flow<UiResourceState<Boolean>>

    suspend fun updateProximityLockStatus(enabled: Boolean)
}
