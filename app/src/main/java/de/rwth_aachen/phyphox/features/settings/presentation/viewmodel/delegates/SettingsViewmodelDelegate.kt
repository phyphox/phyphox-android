package de.rwth_aachen.phyphox.features.settings.presentation.viewmodel.delegates

import kotlinx.coroutines.CoroutineScope

interface SettingsViewmodelDelegate {
    fun start(scope: CoroutineScope)
}
