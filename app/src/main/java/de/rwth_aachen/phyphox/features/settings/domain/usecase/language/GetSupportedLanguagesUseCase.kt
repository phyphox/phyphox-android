package de.rwth_aachen.phyphox.features.settings.domain.usecase.language

import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage
import javax.inject.Inject

class GetSupportedLanguagesUseCase @Inject constructor(
    private val repository: AppPreferencesRepository,
) {

    suspend operator fun invoke(): List<AppLanguage> {
        return listOf(AppLanguage.SYSTEM_DEFAULT) + repository.getSupportedLanguages()
    }
}
