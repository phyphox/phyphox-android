package de.rwth_aachen.phyphox.features.settings.data

import de.rwth_aachen.phyphox.features.settings.domain.data.AppPreferencesRepository
import de.rwth_aachen.phyphox.features.settings.domain.data.local.LocalAppPreferencesDataSource
import de.rwth_aachen.phyphox.features.settings.domain.data.local.converter.toAppLanguage
import de.rwth_aachen.phyphox.features.settings.domain.model.AppLanguage
import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject


class DefaultAppPreferencesRepository @Inject constructor(
    private val localAppPreferencesDataSource: LocalAppPreferencesDataSource,
) : AppPreferencesRepository {
    override fun observeCurrentAccessPort(): Flow<Int?> {
        return localAppPreferencesDataSource.observeCurrentAccessPort()
    }

    override suspend fun updateAccessPort(port: Int) {
        return localAppPreferencesDataSource.updateAccessPort(port)
    }

    override fun observeGraphSize(): Flow<Float?> {
        return localAppPreferencesDataSource.observeGraphSize()
    }

    override suspend fun updateGraphSize(size: Float) {
        localAppPreferencesDataSource.updateGraphSize(size)
    }

    override fun observeCurrentLanguage(): Flow<AppLanguage?> {
        return localAppPreferencesDataSource.observeLanguage().map {
            toAppLanguage(it)
        }
    }

    override suspend fun getSupportedLanguages(): List<AppLanguage> {
        return localAppPreferencesDataSource.getSupportedLanguages().map { AppLanguage(it) }
    }

    override suspend fun updateLanguage(language: AppLanguage) {
        localAppPreferencesDataSource.updateLanguage(language.identifier)
    }

    override fun observeProximityLockEnabled(): Flow<Boolean?> {
        return localAppPreferencesDataSource.observeProximityLockEnabled()
    }

    override suspend fun updateProximityLockStatus(enabled: Boolean) {
        localAppPreferencesDataSource.updateProximityLockEnabled(enabled)
    }

    override fun observeAppUiMode(): Flow<AppUiMode?> {
        return localAppPreferencesDataSource.observeAppUiMode()
    }

    override suspend fun updateAppUiMode(mode: AppUiMode) {
        localAppPreferencesDataSource.updateAppUiMode(mode)
    }
}

