package de.rwth_aachen.phyphox.Bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal fun BluetoothGatt.findCharacteristicOrNull(uuid: UUID): BluetoothGattCharacteristic? {
    for (service in services)
        for (c in service.characteristics)
            if (uuid == c.uuid)
                return c
    return null
}

/** Forwards the GATT completion callbacks to the [BleCommandQueue] returned by [queueProvider]. */
internal open class QueueGattCallback(private val queueProvider: () -> BleCommandQueue?) : BluetoothGattCallback() {

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        queueProvider()?.onEvent(BleEvent.ServicesDiscovered(status == BluetoothGatt.GATT_SUCCESS))
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        queueProvider()?.onEvent(BleEvent.MtuChanged(mtu, status == BluetoothGatt.GATT_SUCCESS))
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        queueProvider()?.onEvent(BleEvent.RssiRead(rssi, status == BluetoothGatt.GATT_SUCCESS))
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        val ok = status == BluetoothGatt.GATT_SUCCESS
        //copy: the stack may reuse the array backing characteristic.value
        queueProvider()?.onEvent(BleEvent.CharacteristicRead(characteristic.uuid, if (ok) characteristic.value?.copyOf() else null, ok))
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        queueProvider()?.onEvent(BleEvent.CharacteristicWritten(characteristic.uuid, status == BluetoothGatt.GATT_SUCCESS))
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        queueProvider()?.onEvent(BleEvent.DescriptorWritten(descriptor.characteristic.uuid, descriptor.uuid, status == BluetoothGatt.GATT_SUCCESS))
    }
}

//Connect handshake shared by the two GATT clients. Direct connect often fails with GATT_ERROR (133) and
// succeeds a moment later, and a board may still be releasing its previous connection, so a refused attempt
// is retried with a fresh client (a refused client left unclosed causes further 133s). The attempt count is
// only a ceiling: a switched-off device burns CONNECT_TIMEOUT_MS per attempt, so the clock bounds it.
@SuppressLint("MissingPermission")
internal class BleConnector(private val context: Context, private val tag: String) {

    @Volatile
    private var connectionEvent: CompletableDeferred<Boolean>? = null

    @Volatile
    var lastStatus = 0
        private set

    var attempts = 0
        private set

    fun onConnectionStateChange(status: Int, newState: Int) {
        lastStatus = status
        connectionEvent?.complete(newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS)
    }

    fun abort() {
        connectionEvent?.complete(false)
    }

    //onGatt publishes each client as soon as it exists and null once a refused one is closed; returns the connected client or null
    suspend fun connect(device: BluetoothDevice, callback: BluetoothGattCallback, onGatt: (BluetoothGatt?) -> Unit): BluetoothGatt? {
        val deadline = SystemClock.elapsedRealtime() + Bluetooth.CONNECT_TOTAL_BUDGET_MS
        for (attempt in 1..Bluetooth.CONNECT_ATTEMPTS) {
            attempts = attempt
            val connected = CompletableDeferred<Boolean>()
            connectionEvent = connected
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            else
                device.connectGatt(context, false, callback)
            onGatt(gatt)
            val ok = gatt != null && withTimeoutOrNull(Bluetooth.CONNECT_TIMEOUT_MS) { connected.await() } == true
            connectionEvent = null
            if (ok) {
                Log.d(tag, "connected to " + device.address + (if (attempt > 1) " on attempt $attempt" else ""))
                return gatt
            }
            Log.w(tag, "connect attempt $attempt of ${Bluetooth.CONNECT_ATTEMPTS} failed (status $lastStatus)")
            gatt?.close()
            onGatt(null)
            if (attempt >= Bluetooth.CONNECT_ATTEMPTS || SystemClock.elapsedRealtime() + Bluetooth.CONNECT_RETRY_DELAY_MS >= deadline)
                break
            delay(Bluetooth.CONNECT_RETRY_DELAY_MS)
        }
        return null
    }
}

/** Executes [BleCommandQueue] operations on the [BluetoothGatt] returned by [gattProvider]. */
@SuppressLint("MissingPermission")
class BleGattIo(private val gattProvider: () -> BluetoothGatt?) : GattIo {

    override fun start(op: BleOp): Boolean {
        val gatt = gattProvider() ?: return false
        return when (op) {
            is BleOp.DiscoverServices -> gatt.discoverServices()
            is BleOp.RequestMtu -> gatt.requestMtu(op.mtu)
            is BleOp.ReadRssi -> gatt.readRemoteRssi()
            is BleOp.Read -> {
                val c = gatt.findCharacteristicOrNull(op.characteristic) ?: return false
                gatt.readCharacteristic(c)
            }
            is BleOp.Write -> {
                val c = gatt.findCharacteristicOrNull(op.characteristic) ?: return false
                // WRITE_TYPE_DEFAULT on purpose: WRITE_TYPE_NO_RESPONSE does not work with the BBC micro:bit
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(c, op.value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    c.setValue(op.value)
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(c)
                }
            }
            is BleOp.WriteDescriptor -> {
                val c = gatt.findCharacteristicOrNull(op.characteristic) ?: return false
                val d = c.getDescriptor(op.descriptor) ?: return false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(d, op.value) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    d.setValue(op.value)
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(d)
                }
            }
        }
    }
}
