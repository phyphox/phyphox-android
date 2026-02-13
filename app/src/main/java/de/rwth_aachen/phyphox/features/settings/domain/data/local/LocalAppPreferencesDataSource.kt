package de.rwth_aachen.phyphox.features.settings.domain.data.local

interface LocalAppPreferencesDataSource :
    LocalAccessPortPreferencesDataSource,
    LocalGraphSizePreferencesDataSource,
    LocalLanguagePreferencesDataSource,
    LocalProximityLockPreferencesDataSource,
    LocalUiConfigPreferencesDataSource

