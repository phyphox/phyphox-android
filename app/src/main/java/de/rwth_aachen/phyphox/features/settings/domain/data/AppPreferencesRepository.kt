package de.rwth_aachen.phyphox.features.settings.domain.data

import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage
import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observeCurrentAccessPort(): Flow<Int?>
    suspend fun updateAccessPort(port: Int)

    fun observeGraphSize(): Flow<Float?>
    suspend fun updateGraphSize(size: Float)

    fun observeCurrentLanguage(): Flow<AppLanguage?>
    suspend fun updateLanguage(language: AppLanguage)
    suspend fun getSupportedLanguages():List<AppLanguage>

    fun observeProximityLockEnabled(): Flow<Boolean?>
    suspend fun updateProximityLockStatus(enabled: Boolean)

    fun observeAppUiMode(): Flow<AppUiMode?>
    suspend fun updateAppUiMode(mode: AppUiMode)
}
