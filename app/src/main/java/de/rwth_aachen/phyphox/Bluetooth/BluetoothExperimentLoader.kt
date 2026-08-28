package de.rwth_aachen.phyphox.Bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.helper.Helper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import kotlin.math.min

/**
 * Downloads an experiment configuration from a BLE device that implements the phyphox service
 * (i.e. a device created with the phyphox Arduino/ESP32 library or any device following the
 * phyphox BLE protocol).
 *
 * Protocol: the experiment characteristic delivers a header packet ("phyphox" + 4 byte size +
 * 4 byte CRC32, big endian) followed by the raw experiment data (a phyphox XML file or a zip
 * archive). If the characteristic supports notifications, the data is streamed via
 * notifications, otherwise it is read repeatedly. If the control characteristic is present, the
 * transfer is started by writing 1 to it (and aborted by writing 0).
 *
 * All GATT operations run through a [BleCommandQueue], so a stalling device fails the transfer
 * with an error dialog after a timeout instead of leaving the user with an eternal progress
 * dialog. The transfer runs as a coroutine on the shared BLE thread of the Bluetooth engine.
 */
@SuppressLint("MissingPermission") //The permission is checked before scanning, which is the only way to get the BluetoothDevice this class works on.
class BluetoothExperimentLoader(private val ctx: Context, private val callback: BluetoothExperimentLoaderCallback) {

    interface BluetoothExperimentLoaderCallback {
        fun updateProgress(transferred: Int, total: Int)
        fun dismiss()
        fun error(msg: String)
        fun success(experimentUri: Uri, isZip: Boolean)
    }

    /**
     * Aborts the transfer with an error message shown to the user. [reason] is the short
     * token the lab records (see Bluetooth.reportBleOutcome); the message is for the user
     * and would not survive being parsed as key=value.
     */
    private class TransferException(val msg: String, val reason: String) : Exception(msg)

    private var gatt: BluetoothGatt? = null
    private var queue: BleCommandQueue? = null
    private var transferJob: Job? = null

    /** completed by onConnectionStateChange while the connection attempt is awaited */
    @Volatile
    private var connectionEvent: CompletableDeferred<Boolean>? = null

    /** notification packets of the experiment characteristic, consumed by the transfer coroutine */
    @Volatile
    private var notifications: Channel<ByteArray>? = null

    /** the characteristic notifications have been enabled for (to disable them on cleanup) */
    private var subscribedCharacteristic: BluetoothGattCharacteristic? = null

    /** whether the device offers the control characteristic (to signal an abort on cleanup) */
    private var hasControlCharacteristic = false

    /** set before cancelling the transfer job to report an asynchronous failure (disconnect, dead link) */
    @Volatile
    private var pendingError: String? = null

    /** guards the final callback (success/error/dismiss), which must be delivered exactly once per transfer */
    @Volatile
    private var finished = false

    /**
     * Set once the payload is complete or the connection is deliberately being released. From
     * that moment a disconnect is what we asked for, not a failure: cleanup() calls
     * gatt.disconnect() itself, and the stack may deliver the disconnect callback before
     * gatt.close() silences it. Without this the callback turned a finished transfer into
     * "connection lost" and cancelled the job while the file was still being written.
     */
    @Volatile
    private var disconnectExpected = false

    /** true while connect() is trying: a disconnect callback then belongs to the attempt it is retrying, not to a transfer */
    @Volatile
    private var connecting = false

    /** the status of the last connection state change, for the retry's log line */
    @Volatile
    private var lastConnectionStatus = 0

    /**
     * Set once the connection is up: from here on there is a transfer to report, whatever
     * happens to it. Only a guard - the reported duration comes from [dataStartMs].
     */
    private var transferStartMs = 0L

    /**
     * When the device was actually asked for the data, i.e. after the subscription and the
     * control write. That is what the reported ms measures, so it is the same quantity
     * board_check.py reports for the same board and the two can be compared; timing from the
     * connection instead would fold in service discovery and make the app look twice as slow
     * as a central that is not phyphox.
     */
    private var dataStartMs = 0L

    /**
     * When the last byte arrived. The success path reports from deliver(), which runs after
     * cleanup() has disconnected, so timing to there would fold the teardown into a figure
     * that is supposed to say how long the DEVICE took.
     */
    private var transferDoneMs = 0L

    fun loadExperimentFromBluetoothDevice(device: BluetoothDevice) {
        val previous = transferJob
        transferJob = Bluetooth.bleScope.launch {
            //only one transfer at a time: wait until a previous one has finished its cleanup and
            // delivered its final callback before the state is reset for this transfer
            previous?.cancel()
            previous?.join()
            pendingError = null
            finished = false
            disconnectExpected = false
            val channel = Channel<ByteArray>(Channel.UNLIMITED)
            notifications = channel
            try {
                val data = runTransfer(device, channel)
                //The transfer is done, so nothing that happens on the connection from here on
                // may take the result away again: release the connection and hand the data over
                // without a cancellation point in between.
                disconnectExpected = true
                withContext(NonCancellable) {
                    cleanup()
                    deliver(data)
                }
            } catch (e: TransferException) {
                Log.e(TAG, "transfer failed: " + e.msg)
                reportTransfer(false, e.reason)
                withContext(NonCancellable) { cleanup() }
                finish { callback.error(e.msg) }
            } catch (e: CancellationException) {
                Log.w(TAG, "transfer cancelled" + (pendingError?.let { ": $it" } ?: " by the user"))
                reportTransfer(false, if (pendingError != null) "link_lost" else "cancelled")
                withContext(NonCancellable) { cleanup() }
                val msg = pendingError
                finish { if (msg != null) callback.error(msg) else callback.dismiss() }
                throw e
            }
        }
    }

    /**
     * Cancel a running transfer (i.e. the user dismissed the progress dialog). The connection is
     * torn down in the background and the device is informed via the control characteristic.
     */
    fun cancel() {
        transferJob?.cancel()
    }

    /**
     * The transfer's line for the lab, once per transfer that actually started. Android does
     * not retry the transfer itself (only the connection under it), so attempts is 1 here -
     * the field exists because iOS does retry a lost transfer and the report is shared.
     * A connection that never came up reports itself as event=connect and is skipped here,
     * so a single failure is never counted twice.
     */
    private fun reportTransfer(ok: Boolean, reason: String? = null, bytes: Int? = null) {
        if (transferStartMs == 0L)
            return
        //Absent rather than wrong when the transfer never got as far as asking for data.
        val end = if (transferDoneMs != 0L) transferDoneMs else SystemClock.elapsedRealtime()
        val ms = if (dataStartMs != 0L) end - dataStartMs else null
        transferStartMs = 0L
        dataStartMs = 0L
        transferDoneMs = 0L
        Bluetooth.reportBleOutcome(TAG, "transfer", 1, ok, reason, bytes, ms)
    }

    private fun finish(deliverResult: () -> Unit) {
        synchronized(this) {
            if (finished)
                return
            finished = true
        }
        deliverResult()
    }

    /**
     * Open the connection, retrying a refused attempt.
     *
     * Android's direct connect fails with GATT_ERROR (133) often enough to matter: three of 22
     * attempts in the lab on 2026-08-28, all on one phone, every one of them arriving as the
     * very first callback with no connection ever established, and the same board connecting
     * again a minute later. It is a property of the moment rather than of the device, so it is
     * worth another attempt instead of an error dialog the user has to answer.
     *
     * Every attempt gets a fresh client and the refused one is closed: a BluetoothGatt that is
     * dropped without close() keeps its registration in the stack, and running out of those is
     * one of the things that produces a 133 in the first place.
     */
    private suspend fun connect(device: BluetoothDevice) {
        connecting = true
        var attemptsMade = 0
        val deadline = SystemClock.elapsedRealtime() + Bluetooth.CONNECT_TOTAL_BUDGET_MS
        try {
            for (attempt in 1..Bluetooth.CONNECT_ATTEMPTS) {
                attemptsMade = attempt
                val connected = CompletableDeferred<Boolean>()
                connectionEvent = connected
                gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                else
                    device.connectGatt(ctx, false, gattCallback)
                val ok = gatt != null && withTimeoutOrNull(Bluetooth.CONNECT_TIMEOUT_MS) { connected.await() } == true
                connectionEvent = null
                if (ok) {
                    Log.d(TAG, "connected to " + device.address
                            + (if (attempt > 1) " on attempt $attempt" else ""))
                    Bluetooth.reportBleOutcome(TAG, "connect", attempt, true)
                    return
                }
                Log.w(TAG, "connect attempt $attempt of ${Bluetooth.CONNECT_ATTEMPTS} failed"
                        + " (status $lastConnectionStatus)")
                gatt?.close()
                gatt = null
                //Bounded by the clock as well as the count: see CONNECT_TOTAL_BUDGET_MS.
                if (attempt >= Bluetooth.CONNECT_ATTEMPTS ||
                        SystemClock.elapsedRealtime() + Bluetooth.CONNECT_RETRY_DELAY_MS >= deadline)
                    break
                delay(Bluetooth.CONNECT_RETRY_DELAY_MS)
            }
        } finally {
            connecting = false
        }
        Bluetooth.reportBleOutcome(TAG, "connect", attemptsMade, false,
                reason = "gatt_$lastConnectionStatus")
        throw TransferException(ctx.getString(R.string.bt_exception_connection), "connect")
    }

    private suspend fun runTransfer(device: BluetoothDevice, channel: Channel<ByteArray>): ByteArray {
        connect(device)
        transferStartMs = SystemClock.elapsedRealtime()

        val q = BleCommandQueue(BleGattIo { gatt }, Bluetooth.bleScope) {
            pendingError = ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (device stopped responding)"
            transferJob?.cancel()
        }
        queue = q

        //Find the phyphox service and its characteristics
        if (!q.run(BleOp.DiscoverServices()).ok)
            throw TransferException(notificationError("could not discover services"), "discovery")
        val service = gatt?.getService(Bluetooth.phyphoxServiceUUID)
            ?: throw TransferException(notificationError("no phyphox service"), "no_service")
        val experimentCharacteristic = service.getCharacteristic(Bluetooth.phyphoxExperimentCharacteristicUUID)
            ?: throw TransferException(notificationError("no experiment characteristic"), "no_characteristic")
        hasControlCharacteristic = service.getCharacteristic(Bluetooth.phyphoxExperimentControlCharacteristicUUID) != null
        Log.d(TAG, "phyphox service found, control characteristic: $hasControlCharacteristic")

        //Enable notifications if the characteristic supports them, otherwise fall back to polling reads
        val useNotifications = (experimentCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        if (useNotifications) {
            if (gatt?.setCharacteristicNotification(experimentCharacteristic, true) != true)
                throw TransferException(notificationError("set char notification failed"), "notify")
            subscribedCharacteristic = experimentCharacteristic
            if (experimentCharacteristic.getDescriptor(BluetoothInput.CONFIG_DESCRIPTOR) == null)
                throw TransferException(notificationError("descriptor failed"), "notify_descriptor")
            if (!q.run(BleOp.WriteDescriptor(experimentCharacteristic.uuid, BluetoothInput.CONFIG_DESCRIPTOR, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)).ok)
                throw TransferException(notificationError("could not write descriptor"), "notify_write")
        }

        //If the control characteristic is present, the device expects us to initiate the transfer by writing 1
        if (hasControlCharacteristic) {
            if (!q.run(BleOp.Write(Bluetooth.phyphoxExperimentControlCharacteristicUUID, byteArrayOf(1))).ok)
                throw TransferException(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (could not write)", "control_write")
        }

        val receivePacket: suspend () -> ByteArray = if (useNotifications) {
            {
                withTimeoutOrNull(DATA_TIMEOUT_MS) { channel.receive() }
                    ?: throw TransferException(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (timeout waiting for data)", "timeout")
            }
        } else {
            {
                val result = q.run(BleOp.Read(experimentCharacteristic.uuid))
                if (!result.ok || result.value == null)
                    throw TransferException(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (read failed)", "read")
                result.value
            }
        }

        //Header: "phyphox" + 4 byte size + 4 byte CRC32 (big endian)
        dataStartMs = SystemClock.elapsedRealtime()
        val header = receivePacket()
        if (header.size < 15 || !String(header, 0, 7).startsWith("phyphox"))
            throw TransferException(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (invalid header)", "header")
        var size = 0
        for (i in 0 until 4)
            size = (size shl 8) or (header[7 + i].toInt() and 0xff)
        var crc = 0L
        for (i in 0 until 4)
            crc = (crc shl 8) or (header[11 + i].toLong() and 0xff)
        if (size < 0 || size > 10_000_000)
            throw TransferException(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (invalid size in header)", "header_size")
        expectedCrc = crc
        Log.d(TAG, "header announces $size bytes")

        //From here on the progress can be estimated: switch to a determinate progress dialog
        callback.dismiss()
        callback.updateProgress(0, size)

        //Payload
        val data = ByteArray(size)
        var index = 0
        while (index < size) {
            val packet = try {
                receivePacket()
            } catch (e: TransferException) {
                //How far a stalled transfer got is the difference between a device that never
                // starts sending and one that stops halfway, and neither is visible afterwards
                // from the error dialog alone.
                Log.e(TAG, "payload stalled after $index of $size bytes")
                throw e
            }
            val length = min(packet.size, size - index)
            System.arraycopy(packet, 0, data, index, length)
            index += length
            callback.updateProgress(index, size)
        }
        transferDoneMs = SystemClock.elapsedRealtime()
        Log.d(TAG, "payload complete, $size bytes")
        return data
    }

    private var expectedCrc = 0L

    /**
     * Check the received data and hand it over as a temporary file (plain phyphox XML or
     * zip archive with assets).
     */
    private suspend fun deliver(data: ByteArray) = withContext(Dispatchers.IO) {
        if (data.isEmpty()) {
            reportTransfer(false, "empty")
            finish { callback.dismiss() }
            return@withContext
        }

        val crc32 = CRC32()
        crc32.update(data)
        if (crc32.value != expectedCrc) {
            Log.e(TAG, "CRC32 mismatch: got " + crc32.value + ", expected " + expectedCrc)
            reportTransfer(false, "crc", data.size)
            finish { callback.error(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (CRC32)") }
            return@withContext
        }

        val tempPath = File(ctx.filesDir, "temp_bt")
        if (!tempPath.exists() && !tempPath.mkdirs()) {
            reportTransfer(false, "tempdir", data.size)
            finish { callback.error("Could not create temporary directory to write bluetooth experiment file.") }
            return@withContext
        }
        tempPath.list()?.forEach { file ->
            if (!File(tempPath, file).delete()) {
                reportTransfer(false, "tempdir_clear", data.size)
                finish { callback.error("Could not clear temporary directory to extract bluetooth experiment file.") }
                return@withContext
            }
        }

        //Element names are matched case-insensitively, so the sniffer for a bare XML file has to fold, too
        val isZip = !String(data, 0, min(8, data.size)).lowercase().startsWith("<phyphox")
        val file = File(tempPath, if (isZip) "bt.zip" else "bt.phyphox")
        try {
            FileOutputStream(file).use { out ->
                out.write(if (isZip) Helper.inflatePartialZip(data) else data)
            }
        } catch (e: Exception) {
            reportTransfer(false, "write", data.size)
            finish { callback.error("Could not write Bluetooth experiment content to " + (if (isZip) "zip" else "phyphox") + " file.") }
            return@withContext
        }

        reportTransfer(true, bytes = data.size)
        Log.i(TAG, "experiment received, " + data.size + " bytes to " + file.name)
        finish { callback.success(Uri.fromFile(file), isZip) }
    }

    /**
     * Release the connection: stop notifications, tell the device that no (further) transfer is
     * expected and close the GATT connection. Failures are ignored, the connection might be gone.
     */
    private suspend fun cleanup() {
        disconnectExpected = true
        val q = queue
        subscribedCharacteristic?.let { c ->
            q?.run(BleOp.WriteDescriptor(c.uuid, BluetoothInput.CONFIG_DESCRIPTOR, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, timeoutMs = CLEANUP_TIMEOUT_MS))
            gatt?.setCharacteristicNotification(c, false)
        }
        subscribedCharacteristic = null
        if (hasControlCharacteristic)
            q?.run(BleOp.Write(Bluetooth.phyphoxExperimentControlCharacteristicUUID, byteArrayOf(0), timeoutMs = CLEANUP_TIMEOUT_MS))
        hasControlCharacteristic = false
        q?.shutdown()
        queue = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private fun notificationError(detail: String): String =
        ctx.getString(R.string.bt_exception_notification) + " " + Bluetooth.phyphoxExperimentCharacteristicUUID.toString() + " " + ctx.getString(R.string.bt_exception_notification_enable) + " (" + detail + ")"

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            //The GATT status is the only thing that tells a refused connection (133) from a
            // supervision timeout (8) or a peer initiated disconnect (19), and it is dropped
            // everywhere below this point, so it is logged here where it still exists.
            Log.d(TAG, "connection state $newState, status $status")
            lastConnectionStatus = status
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectionEvent?.complete(status == BluetoothGatt.GATT_SUCCESS)
                }
                else -> {
                    connectionEvent?.complete(false)
                    //A refused connection attempt belongs to connect(), which retries it and
                    // reports a connection error if it runs out of attempts. Cancelling the
                    // transfer here instead told the user their experiment data was corrupted
                    // when nothing had been transferred at all.
                    if (connecting)
                        return
                    //A disconnect during a running transfer is an error (the old implementation
                    // silently dismissed the progress dialog here) - unless we asked for it.
                    if (transferJob?.isActive == true && !finished && !disconnectExpected) {
                        pendingError = ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (connection lost)"
                        transferJob?.cancel()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            queue?.onEvent(BleEvent.ServicesDiscovered(status == BluetoothGatt.GATT_SUCCESS))
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            queue?.onEvent(
                BleEvent.CharacteristicRead(
                    characteristic.uuid,
                    if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value?.copyOf() else null,
                    status == BluetoothGatt.GATT_SUCCESS
                )
            )
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            queue?.onEvent(BleEvent.CharacteristicWritten(characteristic.uuid, status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            queue?.onEvent(BleEvent.DescriptorWritten(descriptor.characteristic.uuid, descriptor.uuid, status == BluetoothGatt.GATT_SUCCESS))
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != Bluetooth.phyphoxExperimentCharacteristicUUID)
                return
            //copy: the stack may reuse the array backing characteristic.value
            characteristic.value?.let { notifications?.trySend(it.copyOf()) }
        }
    }

    companion object {
        private const val TAG = "phyphoxBleExperiment"

        /** maximum time between two notification packets before the transfer is considered dead */
        const val DATA_TIMEOUT_MS = 10000L

        /** short timeout for the best-effort operations while releasing the connection */
        const val CLEANUP_TIMEOUT_MS = 2000L
    }
}
