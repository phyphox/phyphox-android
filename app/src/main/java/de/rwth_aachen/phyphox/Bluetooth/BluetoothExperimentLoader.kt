package de.rwth_aachen.phyphox.Bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.helper.Helper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.math.min

/**
 * Downloads an experiment from a BLE device implementing the phyphox service. Protocol: a header
 * "phyphox" + 4 byte size + 4 byte CRC32 (big endian), then the payload (XML or zip) via
 * notifications or repeated reads; with a control characteristic, writing 1 starts and 0 aborts.
 */
@SuppressLint("MissingPermission") //checked before the scan that produced the BluetoothDevice
class BluetoothExperimentLoader(private val ctx: Context, private val callback: BluetoothExperimentLoaderCallback) {

    interface BluetoothExperimentLoaderCallback {
        fun updateProgress(transferred: Int, total: Int)
        fun dismiss()
        fun error(msg: String)
        fun success(experimentUri: Uri, isZip: Boolean)
    }

    /** [msg] is shown to the user, [reason] is the short token the lab records (Bluetooth.reportBleOutcome) */
    private class TransferException(val msg: String, val reason: String) : Exception(msg)

    private var gatt: BluetoothGatt? = null
    private var queue: BleCommandQueue? = null
    private var transferJob: Job? = null

    private val connector = BleConnector(ctx, TAG)

    @Volatile
    private var notifications: Channel<ByteArray>? = null

    private var subscribedCharacteristic: BluetoothGattCharacteristic? = null

    private var hasControlCharacteristic = false

    /** set before cancelling the job to turn the cancellation into an error */
    @Volatile
    private var pendingError: String? = null

    /** the final callback is delivered exactly once per transfer */
    @Volatile
    private var finished = false

    /** a disconnect after this was requested by cleanup(); its callback may arrive before close() silences it */
    @Volatile
    private var disconnectExpected = false

    /** a disconnect callback while connect() is trying belongs to the retried attempt, not to a transfer */
    @Volatile
    private var connecting = false

    /** nonzero once there is a transfer to report */
    private var transferStartMs = 0L

    /** start of the reported ms: after subscription and control write, the same quantity board_check.py measures */
    private var dataStartMs = 0L

    /** end of the reported ms: deliver() runs after cleanup() has disconnected and must not be timed */
    private var transferDoneMs = 0L

    fun loadExperimentFromBluetoothDevice(device: BluetoothDevice) {
        val previous = transferJob
        transferJob = Bluetooth.bleScope.launch {
            //one transfer at a time: the previous one finishes cleanup and its final callback first
            previous?.cancel()
            previous?.join()
            pendingError = null
            finished = false
            disconnectExpected = false
            val channel = Channel<ByteArray>(Channel.UNLIMITED)
            notifications = channel
            try {
                val data = runTransfer(device, channel)
                //no cancellation point between releasing the connection and handing over the data
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

    fun cancel() {
        transferJob?.cancel()
    }

    /**
     * The lab line, once per transfer that started (a failed connect reports as event=connect).
     * attempts is always 1: only the connection is retried; iOS retries the transfer, hence the field.
     */
    private fun reportTransfer(ok: Boolean, reason: String? = null, bytes: Int? = null) {
        if (transferStartMs == 0L)
            return
        //ms is absent when the transfer never got as far as asking for data
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

    private suspend fun connect(device: BluetoothDevice) {
        connecting = true
        val ok = try {
            connector.connect(device, gattCallback) { gatt = it } != null
        } finally {
            connecting = false
        }
        Bluetooth.reportBleOutcome(TAG, "connect", connector.attempts, ok,
                reason = if (ok) null else "gatt_${connector.lastStatus}")
        if (!ok)
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

        //Control characteristic: the device starts the transfer on writing 1
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
        val fields = ByteBuffer.wrap(header, 7, 8) //big endian
        val size = fields.int
        val crc = fields.int.toLong() and 0xffffffffL
        if (size < 0 || size > 10_000_000)
            throw TransferException(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (invalid size in header)", "header_size")
        expectedCrc = crc
        Log.d(TAG, "header announces $size bytes")

        //switch to a determinate progress dialog
        callback.dismiss()
        callback.updateProgress(0, size)

        //Payload
        val data = ByteArray(size)
        var index = 0
        while (index < size) {
            val packet = try {
                receivePacket()
            } catch (e: TransferException) {
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

    /** Checks the CRC and hands the data over as a temporary file (XML or zip). */
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

    /** Releases the connection; failures are ignored, it might already be gone. */
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

    private val gattCallback = object : QueueGattCallback({ queue }) {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            //status (133 refused, 8 supervision timeout, 19 peer disconnect) is dropped below this point
            Log.d(TAG, "connection state $newState, status $status")
            connector.onConnectionStateChange(status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED || connecting) //a refused attempt belongs to connect(), which retries it
                return
            //a disconnect during a running transfer is an error unless we asked for it
            if (transferJob?.isActive == true && !finished && !disconnectExpected) {
                pendingError = ctx.getString(R.string.newExperimentBTReadErrorCorrupted) + " (connection lost)"
                transferJob?.cancel()
            }
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

        const val DATA_TIMEOUT_MS = 10000L //between two packets
        const val CLEANUP_TIMEOUT_MS = 2000L
    }
}
