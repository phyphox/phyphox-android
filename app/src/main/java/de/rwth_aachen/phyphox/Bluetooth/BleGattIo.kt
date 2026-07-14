package de.rwth_aachen.phyphox.Bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import java.util.UUID

/**
 * Executes [BleCommandQueue] operations on an actual [BluetoothGatt] (API 33+ value based writes,
 * legacy setValue below). The gatt is obtained through [gattProvider] on every operation, so the
 * owner can replace or close its connection without recreating the queue plumbing.
 */
@SuppressLint("MissingPermission")
class BleGattIo(private val gattProvider: () -> BluetoothGatt?) : GattIo {

    override fun start(op: BleOp): Boolean {
        val gatt = gattProvider() ?: return false
        return when (op) {
            is BleOp.DiscoverServices -> gatt.discoverServices()
            is BleOp.RequestMtu -> gatt.requestMtu(op.mtu)
            is BleOp.ReadRssi -> gatt.readRemoteRssi()
            is BleOp.Read -> {
                val c = findCharacteristicOrNull(gatt, op.characteristic) ?: return false
                gatt.readCharacteristic(c)
            }
            is BleOp.Write -> {
                val c = findCharacteristicOrNull(gatt, op.characteristic) ?: return false
                //Note: WRITE_TYPE_DEFAULT on purpose, WRITE_TYPE_NO_RESPONSE does not work
                // with the BBC micro:bit
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
                val c = findCharacteristicOrNull(gatt, op.characteristic) ?: return false
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

    private fun findCharacteristicOrNull(gatt: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? {
        for (service in gatt.services) {
            for (c in service.characteristics) {
                if (uuid == c.uuid)
                    return c
            }
        }
        return null
    }
}
