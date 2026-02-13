package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates.proximitylock

import de.rwth_aachen.phyphox.features.settings.domain.usecase.proximitylock.ObserveIsCurrentProximityLockEnabledUseCase
import de.rwth_aachen.phyphox.features.settings.domain.usecase.proximitylock.UpdateProximityLockStatusUseCase
import de.rwth_aachen.phyphox.utils.UiResourceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class DefaultProximityLockViewmodelDelegate @Inject constructor(
    private val observeProximityLockEnabled: ObserveIsCurrentProximityLockEnabledUseCase,
    private val updateProximityLockEnabled: UpdateProximityLockStatusUseCase,
) : ProximityLockViewmodelDelegate {

    private val proximityLockEnabledFlow = MutableStateFlow<UiResourceState<Boolean>>(UiResourceState.Loading)

    override val proximityLockFlow: Flow<UiResourceState<Boolean>> = proximityLockEnabledFlow

    override fun start(scope: CoroutineScope) {
        observeProximityLockEnabled()
            .onStart {
                proximityLockEnabledFlow.value = UiResourceState.Loading
            }
            .onEach {
                proximityLockEnabledFlow.value = UiResourceState.Success(it)
            }.launchIn(scope)
    }


    override suspend fun updateProximityLockStatus(enabled: Boolean) {
        updateProximityLockEnabled(enabled)
    }
}
