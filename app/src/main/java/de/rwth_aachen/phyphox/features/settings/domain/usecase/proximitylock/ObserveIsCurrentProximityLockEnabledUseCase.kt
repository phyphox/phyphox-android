package de.rwth_aachen.phyphox.features.settings.domain.usecase.proximitylock

import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class ObserveIsCurrentProximityLockEnabledUseCase @Inject constructor(
    private val repository: AppPreferencesRepository
){
    operator fun invoke(): Flow<Boolean> {
        return flowOf(true)
    }
}
