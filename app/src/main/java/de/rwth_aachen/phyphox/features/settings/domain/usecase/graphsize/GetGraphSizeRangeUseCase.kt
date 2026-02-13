package de.rwth_aachen.phyphox.features.settings.domain.usecase.graphsize

import javax.inject.Inject

@Deprecated("Migrating to multiplier based graph config instead.")
class GetGraphSizeRangeUseCase @Inject constructor() {
    suspend operator fun invoke(): ClosedFloatingPointRange<Float> {
        return 0f..3f
    }
}
