package de.rwth_aachen.phyphox.common

import kotlinx.coroutines.CoroutineScope

interface AppDelegate {
    fun start(coroutineScope: CoroutineScope)
    fun stop()
}
