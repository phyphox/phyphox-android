package de.rwth_aachen.phyphox.features.settings.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.rwth_aachen.phyphox.features.settings.di.SettingsDataStore
import de.rwth_aachen.phyphox.features.settings.domain.data.local.LocalAppPreferencesDataSource
import de.rwth_aachen.phyphox.features.settings.domain.model.AppUiMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLocalAppPreferencesDataSource @Inject constructor(
    @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    private val systemDataSource: SystemDataSource,
) : LocalAppPreferencesDataSource {

    override fun observeCurrentAccessPort(): Flow<Int?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ACCESS_PORT]
    }

    override suspend fun updateAccessPort(port: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_PORT] = port
        }
    }

    override fun observeGraphSize(): Flow<Float?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GRAPH_SIZE]
    }

    override suspend fun updateGraphSize(size: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GRAPH_SIZE] = size
        }
    }

    override fun observeLanguage(): Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LANGUAGE]
    }

    override suspend fun updateLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language
        }
    }

    override suspend fun getSupportedLanguages(): List<String> {
        return systemDataSource.getSupportedLanguages()
    }

    override fun observeProximityLockEnabled(): Flow<Boolean?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PROXIMITY_LOCK_ENABLED]
    }

    override suspend fun updateProximityLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROXIMITY_LOCK_ENABLED] = enabled
        }
    }

    override fun observeAppUiMode(): Flow<AppUiMode?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.APP_UI_MODE]?.let { AppUiMode.fromString(it) }
    }

    override suspend fun updateAppUiMode(mode: AppUiMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_UI_MODE] = mode.identifier
        }
    }

    private object PreferencesKeys {
        val ACCESS_PORT = intPreferencesKey("access_port")
        val GRAPH_SIZE = floatPreferencesKey("graph_size")
        val LANGUAGE = stringPreferencesKey("language")
        val PROXIMITY_LOCK_ENABLED = booleanPreferencesKey("proximity_lock_enabled")
        val APP_UI_MODE = stringPreferencesKey("app_ui_mode")
    }

}
