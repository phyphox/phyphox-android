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

//Serialises GATT operations (Android allows one in flight; completion arrives as a callback).
// - onEvent() matches a callback to the running op by type and UUID, unmatched events are ignored
// - a timed-out op fails and the queue continues; LINK_DEAD_TIMEOUT_COUNT in a row -> onLinkDead()
// - coalescing: a queued coalescible write is replaced by a newer one to the same characteristic,
//   queued reads of the same characteristic are deduplicated, control ops are never coalesced
//No Android dependencies (GattIo abstraction), so the logic is unit tested on the JVM.

//Starts an asynchronous GATT operation; returns whether it was started, the result arrives via onEvent()
interface GattIo {
    fun start(op: BleOp): Boolean
}

sealed class BleOp(val timeoutMs: Long) {

    class DiscoverServices(timeoutMs: Long = LONG_TIMEOUT_MS) : BleOp(timeoutMs)

    class RequestMtu(val mtu: Int, timeoutMs: Long = LONG_TIMEOUT_MS) : BleOp(timeoutMs)

    class Read(val characteristic: UUID, timeoutMs: Long = DEFAULT_TIMEOUT_MS) : BleOp(timeoutMs)

    //coalescible: data writes only, never control or config writes
    class Write(val characteristic: UUID, val value: ByteArray, val coalescible: Boolean = false, timeoutMs: Long = DEFAULT_TIMEOUT_MS) : BleOp(timeoutMs)

    class WriteDescriptor(val characteristic: UUID, val descriptor: UUID, val value: ByteArray, timeoutMs: Long = DEFAULT_TIMEOUT_MS) : BleOp(timeoutMs)

    class ReadRssi(timeoutMs: Long = DEFAULT_TIMEOUT_MS) : BleOp(timeoutMs)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L //generous on purpose, timeouts only detect a dead link
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

    //called from the worker when LINK_DEAD_TIMEOUT_COUNT ops timed out in a row without a disconnect callback
    fun interface Listener {
        fun onLinkDead()
    }

    private class PendingOp(val op: BleOp) {
        val result = CompletableDeferred<BleResult>()
    }

    //enqueue/clear/onEvent may be called from any thread, so pending is guarded by lock; only the worker executes ops
    private val lock = Any()
    private val pending = ArrayDeque<PendingOp>()
    @Volatile private var running: PendingOp? = null //also read by onEvent from GATT callback threads
    private var consecutiveTimeouts = 0
    private val wakeup = Channel<Unit>(Channel.CONFLATED)
    private val worker: Job

    private val maxQueueLength = 64 //safety net against a misbehaving caller

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

    fun enqueue(op: BleOp): Deferred<BleResult> {
        var replaced: PendingOp? = null
        val result: Deferred<BleResult>
        synchronized(lock) {
            if (op is BleOp.Write && op.coalescible) {
                replaced = pending.firstOrNull { it.op is BleOp.Write && it.op.coalescible && it.op.characteristic == op.characteristic }
                replaced?.let { pending.remove(it) }
            }
            if (op is BleOp.Read) {
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

    suspend fun run(op: BleOp): BleResult = enqueue(op).await()

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
        //unmatched events (late callbacks of timed-out ops, unsolicited MTU changes) are ignored on purpose
    }

    private fun matches(op: BleOp, event: BleEvent): Boolean = when (op) {
        is BleOp.DiscoverServices -> event is BleEvent.ServicesDiscovered
        is BleOp.RequestMtu -> event is BleEvent.MtuChanged
        is BleOp.Read -> event is BleEvent.CharacteristicRead && event.characteristic == op.characteristic
        is BleOp.Write -> event is BleEvent.CharacteristicWritten && event.characteristic == op.characteristic
        is BleOp.WriteDescriptor -> event is BleEvent.DescriptorWritten && event.characteristic == op.characteristic && event.descriptor == op.descriptor
        is BleOp.ReadRssi -> event is BleEvent.RssiRead
    }

    //the running op is not cancelled, it completes or times out on its own
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
        running?.result?.complete(BleResult(BleResult.Status.CANCELLED)) //unblock a caller awaiting the running op
        worker.cancel()
    }

    companion object {
        const val LINK_DEAD_TIMEOUT_COUNT = 3
    }
}
