package de.rwth_aachen.phyphox.features.settings.domain.data.local

import kotlinx.coroutines.flow.Flow

interface LocalLanguagePreferencesDataSource {
    fun observeLanguage(): Flow<String?>
    suspend fun updateLanguage(language: String)
    suspend fun getSupportedLanguages(): List<String>
}
