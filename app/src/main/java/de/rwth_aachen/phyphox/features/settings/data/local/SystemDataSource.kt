package de.rwth_aachen.phyphox.features.settings.data.local

import de.rwth_aachen.phyphox.BuildConfig
import javax.inject.Inject

class SystemDataSource @Inject constructor() {
    suspend fun getSupportedLanguages(): List<String> {
        return BuildConfig.LOCALE_ARRAY
            .filterNot { it.contains("+") }
            .map { it.replace("-r", "-") }
    }
}
