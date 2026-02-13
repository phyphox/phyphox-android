package de.rwth_aachen.phyphox.features.settings.domain.usecase.graphsize

import javax.inject.Inject

class GetLineWidthMultiplierRangeDomainService @Inject constructor() {
    suspend operator fun invoke(): ClosedFloatingPointRange<Float> {
        return 0.5f..2.5f
    }
}
