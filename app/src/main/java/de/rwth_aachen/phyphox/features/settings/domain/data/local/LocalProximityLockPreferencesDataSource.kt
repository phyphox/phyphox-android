package de.rwth_aachen.phyphox.features.settings.domain.data.local

import kotlinx.coroutines.flow.Flow

interface LocalProximityLockPreferencesDataSource {
    fun observeProximityLockEnabled(): Flow<Boolean?>
    suspend fun updateProximityLockEnabled(enabled: Boolean)
}
