package de.rwth_aachen.phyphox.Bluetooth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

//JVM tests of the BLE operation queue. A fake GattIo stands in for the Android BLE stack: it
// records the started operations and lets the test control which callbacks arrive and when.
class BleCommandQueueTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val uuidA: UUID = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")
    private val uuidB: UUID = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")

    private val shortTimeout = 100L //for operations that are meant to time out in tests

    private class RecordingIo : GattIo {
        val started = CopyOnWriteArrayList<BleOp>()
        var acceptOps = true
        var queue: BleCommandQueue? = null

        //If set, started operations are completed asynchronously with the returned event
        @Volatile
        var autoComplete: ((BleOp) -> BleEvent?)? = null

        override fun start(op: BleOp): Boolean {
            if (!acceptOps)
                return false
            started.add(op)
            autoComplete?.invoke(op)?.let { event ->
                Thread { queue?.onEvent(event) }.start()
            }
            return true
        }
    }

    private var linkDeadCount = 0

    private fun makeQueue(io: RecordingIo): BleCommandQueue {
        val queue = BleCommandQueue(io, scope) { linkDeadCount++ }
        io.queue = queue
        return queue
    }

    private fun completeReadsAndWrites(io: RecordingIo) {
        io.autoComplete = { op ->
            when (op) {
                is BleOp.Read -> BleEvent.CharacteristicRead(op.characteristic, byteArrayOf(1), true)
                is BleOp.Write -> BleEvent.CharacteristicWritten(op.characteristic, true)
                else -> null
            }
        }
    }

    //A read that gets no answer and therefore times out after shortTimeout, keeping the worker
    // busy meanwhile - used to make subsequent operations queue up
    private fun blockerOp() = BleOp.Read(uuidA, timeoutMs = shortTimeout)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun executesInOrderAndOneAtATime() = runBlocking {
        val io = RecordingIo()
        completeReadsAndWrites(io)
        val queue = makeQueue(io)

        val results = listOf(
            queue.enqueue(BleOp.Write(uuidA, byteArrayOf(1))),
            queue.enqueue(BleOp.Read(uuidB)),
            queue.enqueue(BleOp.Write(uuidB, byteArrayOf(2)))
        )
        withTimeout(5000) { results.forEach { assertThat(it.await().ok).isTrue() } }

        assertThat(io.started).hasSize(3)
        assertThat(io.started[0]).isInstanceOf(BleOp.Write::class.java)
        assertThat(io.started[1]).isInstanceOf(BleOp.Read::class.java)
        assertThat(io.started[2]).isInstanceOf(BleOp.Write::class.java)
    }

    @Test
    fun timeoutFailsTheOperationButQueueContinues() = runBlocking {
        val io = RecordingIo()
        val queue = makeQueue(io)

        val first = queue.enqueue(blockerOp())
        val result1 = withTimeout(5000) { first.await() }
        assertThat(result1.status).isEqualTo(BleResult.Status.TIMEOUT)

        //After the timeout the queue must execute the next operation
        io.autoComplete = { op ->
            if (op is BleOp.Read) BleEvent.CharacteristicRead(op.characteristic, byteArrayOf(42), true) else null
        }
        val second = queue.enqueue(BleOp.Read(uuidB))
        val result2 = withTimeout(5000) { second.await() }
        assertThat(result2.ok).isTrue()
        assertThat(result2.value).isEqualTo(byteArrayOf(42))
    }

    @Test
    fun consecutiveTimeoutsRaiseLinkDead() = runBlocking {
        val io = RecordingIo()
        val queue = makeQueue(io)

        repeat(BleCommandQueue.LINK_DEAD_TIMEOUT_COUNT) {
            withTimeout(5000) { queue.enqueue(blockerOp()).await() }
        }
        assertThat(linkDeadCount).isEqualTo(1)
    }

    @Test
    fun successResetsTimeoutCounter() = runBlocking {
        val io = RecordingIo()
        val queue = makeQueue(io)

        withTimeout(5000) { queue.enqueue(blockerOp()).await() }
        withTimeout(5000) { queue.enqueue(blockerOp()).await() }

        io.autoComplete = { op ->
            if (op is BleOp.Read && op.characteristic == uuidB) BleEvent.CharacteristicRead(op.characteristic, byteArrayOf(1), true) else null
        }
        withTimeout(5000) { queue.enqueue(BleOp.Read(uuidB)).await() }

        io.autoComplete = null
        withTimeout(5000) { queue.enqueue(blockerOp()).await() }

        assertThat(linkDeadCount).isEqualTo(0)
    }

    @Test
    fun coalescibleWritesAreReplacedByNewerValue() = runBlocking {
        val io = RecordingIo()
        val queue = makeQueue(io)

        val blocker = queue.enqueue(blockerOp())

        val w1 = queue.enqueue(BleOp.Write(uuidB, byteArrayOf(1), coalescible = true))
        val w2 = queue.enqueue(BleOp.Write(uuidB, byteArrayOf(2), coalescible = true))
        val w3 = queue.enqueue(BleOp.Write(uuidB, byteArrayOf(3), coalescible = true))

        io.autoComplete = { op ->
            if (op is BleOp.Write) BleEvent.CharacteristicWritten(op.characteristic, true) else null
        }

        withTimeout(5000) { blocker.await() }
        assertThat(withTimeout(5000) { w1.await() }.status).isEqualTo(BleResult.Status.CANCELLED)
        assertThat(withTimeout(5000) { w2.await() }.status).isEqualTo(BleResult.Status.CANCELLED)
        assertThat(withTimeout(5000) { w3.await() }.ok).isTrue()

        //only the newest write was actually transmitted
        val writes = io.started.filterIsInstance<BleOp.Write>()
        assertThat(writes).hasSize(1)
        assertThat(writes[0].value).isEqualTo(byteArrayOf(3))
    }

    @Test
    fun pendingReadsOfSameCharacteristicAreDeduplicated() = runBlocking {
        val io = RecordingIo()
        val queue = makeQueue(io)

        val blocker = queue.enqueue(blockerOp())

        val r1 = queue.enqueue(BleOp.Read(uuidB))
        val r2 = queue.enqueue(BleOp.Read(uuidB))
        assertThat(r1).isSameInstanceAs(r2)

        io.autoComplete = { op ->
            if (op is BleOp.Read && op.characteristic == uuidB) BleEvent.CharacteristicRead(op.characteristic, byteArrayOf(7), true) else null
        }
        withTimeout(5000) { blocker.await() }
        assertThat(withTimeout(5000) { r1.await() }.value).isEqualTo(byteArrayOf(7))
        assertThat(io.started.filterIsInstance<BleOp.Read>().count { it.characteristic == uuidB }).isEqualTo(1)
    }

    @Test
    fun controlWritesAreNotCoalesced() = runBlocking {
        val io = RecordingIo()
        val queue = makeQueue(io)

        val blocker = queue.enqueue(blockerOp())

        val w1 = queue.enqueue(BleOp.Write(uuidB, byteArrayOf(1)))
        val w2 = queue.enqueue(BleOp.Write(uuidB, byteArrayOf(2)))

        io.autoComplete = { op ->
            if (op is BleOp.Write) BleEvent.CharacteristicWritten(op.characteristic, true) else null
        }
        withTimeout(5000) { blocker.await() }
        assertThat(withTimeout(5000) { w1.await() }.ok).isTrue()
        assertThat(withTimeout(5000) { w2.await() }.ok).isTrue()
        assertThat(io.started.filterIsInstance<BleOp.Write>()).hasSize(2)
    }

    @Test
    fun clearCancelsPendingOperations() = runBlocking {
        val io = RecordingIo()
        val queue = makeQueue(io)

        val blocker = queue.enqueue(blockerOp())
        val pending = queue.enqueue(BleOp.Write(uuidB, byteArrayOf(1)))
        queue.clear()

        assertThat(withTimeout(5000) { pending.await() }.status).isEqualTo(BleResult.Status.CANCELLED)
        withTimeout(5000) { blocker.await() } //the running op still completes (times out) on its own
        assertThat(io.started.filterIsInstance<BleOp.Write>()).isEmpty()
    }

    @Test
    fun unmatchedEventsAreIgnored() = runBlocking {
        val io = RecordingIo()
        val queue = makeQueue(io)

        val read = queue.enqueue(blockerOp()) //read of uuidA
        delay(20) //let the read start
        //Events that do not match the running operation must not complete it
        queue.onEvent(BleEvent.CharacteristicRead(uuidB, byteArrayOf(9), true)) //wrong characteristic
        queue.onEvent(BleEvent.CharacteristicWritten(uuidA, true)) //wrong type
        queue.onEvent(BleEvent.MtuChanged(100, true)) //unsolicited

        val result = withTimeout(5000) { read.await() }
        assertThat(result.status).isEqualTo(BleResult.Status.TIMEOUT)
    }

    @Test
    fun failedStartFailsImmediately() = runBlocking {
        val io = RecordingIo()
        io.acceptOps = false
        val queue = makeQueue(io)

        val result = withTimeout(5000) { queue.enqueue(BleOp.Read(uuidA)).await() }
        assertThat(result.status).isEqualTo(BleResult.Status.FAILURE)
    }
}
