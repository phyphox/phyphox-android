package de.rwth_aachen.phyphox.features.settings.domain.data.local

import kotlinx.coroutines.flow.Flow

interface LocalGraphSizePreferencesDataSource {
    fun observeGraphSize(): Flow<Float?>
    suspend fun updateGraphSize(size: Float)
}
