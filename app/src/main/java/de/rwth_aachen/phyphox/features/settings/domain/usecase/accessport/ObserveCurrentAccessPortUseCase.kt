package de.rwth_aachen.phyphox.features.settings.domain.usecase.accessport

import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveCurrentAccessPortUseCase @Inject constructor(
    private val repository: AppPreferencesRepository,
) {
    operator fun invoke(): Flow<Int> {
        return repository.observeCurrentAccessPort().map { port ->
            port ?: DEFAULT_ACCESS_PORT
        }
    }

    companion object {
        const val DEFAULT_ACCESS_PORT = 8080
    }
}
