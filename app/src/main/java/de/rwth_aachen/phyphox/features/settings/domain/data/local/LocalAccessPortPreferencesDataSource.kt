package de.rwth_aachen.phyphox.features.settings.domain.data.local

import kotlinx.coroutines.flow.Flow

interface LocalAccessPortPreferencesDataSource {
    fun observeCurrentAccessPort(): Flow<Int?>
    suspend fun updateAccessPort(port: Int)
}
