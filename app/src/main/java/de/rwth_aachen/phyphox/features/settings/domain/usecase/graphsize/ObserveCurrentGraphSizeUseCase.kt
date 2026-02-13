package de.rwth_aachen.phyphox.features.settings.domain.usecase.graphsize

import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@Deprecated("Migrating to multiplier based graph config instead.")
class ObserveCurrentGraphSizeUseCase @Inject constructor(
    private val repository: AppPreferencesRepository
) {
    operator fun invoke(): Flow<Float> {
        return flowOf(0f)
    }
}
