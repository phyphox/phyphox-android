package de.rwth_aachen.phyphox.features.settings.domain.usecase.language

import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage
import javax.inject.Inject

class UpdateAppLanguageUseCase @Inject constructor(
    private val repository: AppPreferencesRepository,
) {
    suspend operator fun invoke(identifier: String): Result<Unit> {
        return invoke(AppLanguage(identifier))
    }

    suspend operator fun invoke(language: AppLanguage): Result<Unit> {
        return try {
            repository.updateLanguage(language)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
