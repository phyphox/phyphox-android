package de.rwth_aachen.phyphox.features.settings.domain.usecase.uimode

import de.rwth_aachen.phyphox.di.ApplicationScope
import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


class ObserveCurrentAppUiModeUseCase @Inject constructor(
    private val repository: AppPreferencesRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    operator fun invoke(): StateFlow<AppUiMode> {
        return repository.observeAppUiMode().map { it ?: AppUiMode.SYSTEM }.distinctUntilChanged().stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = AppUiMode.SYSTEM,
            )
    }
}
