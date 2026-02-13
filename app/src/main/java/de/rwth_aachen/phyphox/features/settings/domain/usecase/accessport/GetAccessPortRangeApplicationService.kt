package de.rwth_aachen.phyphox.features.settings.domain.usecase.accessport

import javax.inject.Inject

class GetAccessPortRangeApplicationService @Inject constructor() {
    operator fun invoke(): IntRange {
        return MIN_PORT..MAX_PORT
    }

    companion object {
        const val MIN_PORT = 1024
        const val MAX_PORT = 65536
    }
}
