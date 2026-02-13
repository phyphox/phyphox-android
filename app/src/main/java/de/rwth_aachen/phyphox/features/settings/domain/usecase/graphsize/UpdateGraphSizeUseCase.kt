package de.rwth_aachen.phyphox.features.settings.domain.usecase.graphsize

import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import javax.inject.Inject

class UpdateGraphSizeUseCase @Inject constructor(
    private val repository: AppPreferencesRepository
){
}
