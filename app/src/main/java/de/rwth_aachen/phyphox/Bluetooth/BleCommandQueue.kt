package de.rwth_aachen.phyphox.Bluetooth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

//The event driven command queue at the heart of the Bluetooth engine.
//
//The Android BLE stack only allows a single GATT operation to be in flight at any time and
// reports its completion through asynchronous callbacks.
// - Operations (BleOp) are enqueued and executed strictly one at a time by a worker coroutine.
// - Completion is signaled by feeding the corresponding GATT callback into onEvent(). Events
//   are matched against the running operation by type and UUID, so a late or unrelated callback
//   can never complete the wrong operation.
// - Every operation has a timeout. A timed-out operation fails, but the queue continues with
//   the next one. Several timeouts in a row indicate a dead link and are reported to the
//   listener, which can then tear down the connection and reconnect.
// - Backpressure on slow connections is handled by coalescing instead of dropping arbitrary
//   commands: data writes replace a queued (not yet started) write to the same characteristic,
//   so always the newest value is transmitted, and reads of the same characteristic are
//   deduplicated. Control operations (service discovery, MTU, descriptor and config writes)
//   are never coalesced.
//
//The queue itself is free of Android dependencies (operations are identified by their UUIDs
// and executed through the GattIo interface), so its logic can be unit tested on the JVM.

//Abstraction of the underlying BluetoothGatt: initiates an asynchronous operation and returns
// whether the operation was successfully started. The result arrives later via onEvent().
interface GattIo {
    fun start(op: BleOp): Boolean
}

//The operations that can be queued. Timeouts are generous compared to typical BLE latencies,
// they only serve to detect a dead or unresponsive connection.
sealed class BleOp(val timeoutMs: Long) {

    class DiscoverServices(timeoutMs: Long = LONG_TIMEOUT_MS) : BleOp(timeoutMs)

    class RequestMtu(val mtu: Int, timeoutMs: Long = LONG_TIMEOUT_MS) : BleOp(timeoutMs)

    class Read(val characteristic: UUID, timeoutMs: Long = DEFAULT_TIMEOUT_MS) : BleOp(timeoutMs)

    //coalescible = true for data writes that may be replaced by a newer value to the same
    // characteristic while waiting in the queue. Control and config writes pass false.
    class Write(val characteristic: UUID, val value: ByteArray, val coalescible: Boolean = false, timeoutMs: Long = DEFAULT_TIMEOUT_MS) : BleOp(timeoutMs)

    class WriteDescriptor(val characteristic: UUID, val descriptor: UUID, val value: ByteArray, timeoutMs: Long = DEFAULT_TIMEOUT_MS) : BleOp(timeoutMs)

    class ReadRssi(timeoutMs: Long = DEFAULT_TIMEOUT_MS) : BleOp(timeoutMs)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val LONG_TIMEOUT_MS = 10000L
    }
}

//GATT callback results fed into the queue via onEvent()
sealed class BleEvent {
    abstract val success: Boolean

    class ServicesDiscovered(override val success: Boolean) : BleEvent()
    class MtuChanged(val mtu: Int, override val success: Boolean) : BleEvent()
    class CharacteristicRead(val characteristic: UUID, val value: ByteArray?, override val success: Boolean) : BleEvent()
    class CharacteristicWritten(val characteristic: UUID, override val success: Boolean) : BleEvent()
    class DescriptorWritten(val characteristic: UUID, val descriptor: UUID, override val success: Boolean) : BleEvent()
    class RssiRead(val rssi: Int, override val success: Boolean) : BleEvent()
}

class BleResult(val status: Status, val value: ByteArray? = null, val rssi: Int = 0, val mtu: Int = 0) {
    enum class Status {
        SUCCESS,    //the operation completed successfully
        FAILURE,    //the operation could not be started or the GATT callback reported an error
        TIMEOUT,    //no matching GATT callback arrived within the operation's timeout
        CANCELLED   //the queue was cleared or the operation was replaced by a newer one
    }

    val ok get() = status == Status.SUCCESS
}

class BleCommandQueue(private val io: GattIo, scope: CoroutineScope, private val listener: Listener? = null) {

    //Notified (from the worker context) when consecutive operations time out, i.e. the device
    // is most likely gone even though no disconnect callback arrived.
    fun interface Listener {
        fun onLinkDead()
    }

    private class PendingOp(val op: BleOp) {
        val result = CompletableDeferred<BleResult>()
    }

    //enqueue/clear/onEvent may be called from any thread (main thread, analysis thread, GATT
    // binder threads), so the pending list is guarded by a lock. Only the worker executes ops.
    private val lock = Any()
    private val pending = ArrayDeque<PendingOp>()
    @Volatile private var running: PendingOp? = null //also read by onEvent from GATT callback threads
    private var consecutiveTimeouts = 0
    private val wakeup = Channel<Unit>(Channel.CONFLATED)
    private val worker: Job

    //Safety net: with coalescing in place the queue should stay short, but a misbehaving caller
    // must not be able to grow it without bounds.
    private val maxQueueLength = 64

    init {
        worker = scope.launch {
            while (true) {
                val next = synchronized(lock) { pending.removeFirstOrNull() }
                if (next == null) {
                    wakeup.receive()
                    continue
                }
                running = next
                execute(next)
                running = null
            }
        }
    }

    private suspend fun execute(pendingOp: PendingOp) {
        val started = try {
            io.start(pendingOp.op)
        } catch (e: Exception) {
            false
        }
        if (!started) {
            pendingOp.result.complete(BleResult(BleResult.Status.FAILURE))
            return
        }
        val result = try {
            withTimeout(pendingOp.op.timeoutMs) {
                pendingOp.result.await()
            }
        } catch (e: TimeoutCancellationException) {
            BleResult(BleResult.Status.TIMEOUT)
        }
        pendingOp.result.complete(result) //no-op if already completed by onEvent

        if (result.status == BleResult.Status.TIMEOUT) {
            consecutiveTimeouts++
            if (consecutiveTimeouts >= LINK_DEAD_TIMEOUT_COUNT)
                listener?.onLinkDead()
        } else {
            consecutiveTimeouts = 0
        }
    }

    //Enqueue an operation. Returns a Deferred with the eventual result. Thread safe.
    fun enqueue(op: BleOp): Deferred<BleResult> {
        var replaced: PendingOp? = null
        val result: Deferred<BleResult>
        synchronized(lock) {
            if (op is BleOp.Write && op.coalescible) {
                //Replace a queued (not yet running) write to the same characteristic
                replaced = pending.firstOrNull { it.op is BleOp.Write && it.op.coalescible && it.op.characteristic == op.characteristic }
                replaced?.let { pending.remove(it) }
            }
            if (op is BleOp.Read) {
                //A queued read of the same characteristic will deliver the same fresh value
                val existing = pending.firstOrNull { it.op is BleOp.Read && it.op.characteristic == op.characteristic }
                if (existing != null)
                    return existing.result
            }
            val pendingOp = PendingOp(op)
            if (pending.size >= maxQueueLength) {
                pendingOp.result.complete(BleResult(BleResult.Status.FAILURE))
                return pendingOp.result
            }
            pending.addLast(pendingOp)
            result = pendingOp.result
        }
        replaced?.result?.complete(BleResult(BleResult.Status.CANCELLED))
        wakeup.trySend(Unit)
        return result
    }

    //Enqueue and await the result
    suspend fun run(op: BleOp): BleResult = enqueue(op).await()

    //Feed a GATT callback into the queue. May be called from any thread; completing the
    // deferred is thread safe and the worker holds all other state.
    fun onEvent(event: BleEvent) {
        val current = running ?: return
        if (matches(current.op, event)) {
            val result = if (event.success)
                when (event) {
                    is BleEvent.CharacteristicRead -> BleResult(BleResult.Status.SUCCESS, value = event.value)
                    is BleEvent.RssiRead -> BleResult(BleResult.Status.SUCCESS, rssi = event.rssi)
                    is BleEvent.MtuChanged -> BleResult(BleResult.Status.SUCCESS, mtu = event.mtu)
                    else -> BleResult(BleResult.Status.SUCCESS)
                }
            else
                BleResult(BleResult.Status.FAILURE)
            current.result.complete(result)
        }
        //Unmatched events (late callbacks of timed-out operations, unsolicited MTU changes...)
        // are intentionally ignored: they must never complete an unrelated operation.
    }

    private fun matches(op: BleOp, event: BleEvent): Boolean = when (op) {
        is BleOp.DiscoverServices -> event is BleEvent.ServicesDiscovered
        is BleOp.RequestMtu -> event is BleEvent.MtuChanged
        is BleOp.Read -> event is BleEvent.CharacteristicRead && event.characteristic == op.characteristic
        is BleOp.Write -> event is BleEvent.CharacteristicWritten && event.characteristic == op.characteristic
        is BleOp.WriteDescriptor -> event is BleEvent.DescriptorWritten && event.characteristic == op.characteristic && event.descriptor == op.descriptor
        is BleOp.ReadRssi -> event is BleEvent.RssiRead
    }

    //Cancel all queued operations (the running one completes or times out on its own). Called
    // on stop, disconnect and before a reconnection attempt. Thread safe.
    fun clear() {
        val cancelled = synchronized(lock) {
            val copy = ArrayList(pending)
            pending.clear()
            copy
        }
        for (pendingOp in cancelled)
            pendingOp.result.complete(BleResult(BleResult.Status.CANCELLED))
        consecutiveTimeouts = 0
    }

    fun shutdown() {
        clear()
        //complete a possibly running operation so no caller stays blocked on its result
        running?.result?.complete(BleResult(BleResult.Status.CANCELLED))
        worker.cancel()
    }

    companion object {
        const val LINK_DEAD_TIMEOUT_COUNT = 3
    }
}
