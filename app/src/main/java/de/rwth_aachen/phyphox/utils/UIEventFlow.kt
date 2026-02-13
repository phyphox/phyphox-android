package de.rwth_aachen.phyphox.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class UIEventFlow<T> : AbstractFlow<T>() {

    private val events = Channel<T>(capacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override suspend fun collectSafely(collector: FlowCollector<T>) {
        events.receiveAsFlow().collect {
            collector.emit(it)
        }
    }

    suspend fun emit(event: T) {
        withContext(Dispatchers.Main.immediate) {
            events.send(event)
        }
    }
}

fun <T> UIEventFlow<T>.asFlow(): Flow<T> = this
