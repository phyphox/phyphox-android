package de.rwth_aachen.phyphox.features.settings.domain.data.local

import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import kotlinx.coroutines.flow.Flow

interface LocalUiConfigPreferencesDataSource {
    fun observeAppUiMode(): Flow<AppUiMode?>
    suspend fun updateAppUiMode(mode: AppUiMode)
}
