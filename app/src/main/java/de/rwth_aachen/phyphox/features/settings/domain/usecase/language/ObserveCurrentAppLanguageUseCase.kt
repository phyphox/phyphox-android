package de.rwth_aachen.phyphox.features.settings.domain.usecase.language

import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveCurrentAppLanguageUseCase @Inject constructor(
    private val repository: AppPreferencesRepository,
) {
    operator fun invoke(): Flow<AppLanguage> {
        return repository.observeCurrentLanguage().map { it ?: AppLanguage.SYSTEM_DEFAULT }
    }
}
