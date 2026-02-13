package de.rwth_aachen.phyphox.features.settings.domain.usecase.uimode

import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import javax.inject.Inject

class GetSupportedAppUiModeUseCase @Inject constructor() {
    operator fun invoke(): List<AppUiMode> {
        return AppUiMode.entries
    }
}
