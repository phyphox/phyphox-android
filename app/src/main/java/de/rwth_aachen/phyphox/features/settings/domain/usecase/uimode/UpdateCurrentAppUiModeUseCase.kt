package de.rwth_aachen.phyphox.features.settings.domain.usecase.uimode

import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import javax.inject.Inject

class UpdateCurrentAppUiModeUseCase @Inject constructor(
    private val repository: AppPreferencesRepository,
) {
    suspend operator fun invoke(appUiMode: AppUiMode): Result<Unit> {
        return try {
            repository.updateAppUiMode(appUiMode)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
