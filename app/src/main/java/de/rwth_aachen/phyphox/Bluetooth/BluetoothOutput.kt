package de.rwth_aachen.phyphox.Bluetooth

import android.app.Activity
import android.content.Context
import de.rwth_aachen.phyphox.DataBuffer
import de.rwth_aachen.phyphox.DataInput
import java.util.UUID
import java.util.Vector

/**
 * A Bluetooth Low Energy device that receives buffer data from the experiment after each
 * analysis cycle (or when triggered by a button with a triggerId).
 */
class BluetoothOutput(
    idString: String?,
    deviceName: String?,
    deviceAddress: String?,
    uuidFilter: UUID?,
    autoConnect: Boolean,
    activity: Activity,
    context: Context,
    /** data buffers the sent values are read from (indexed by Characteristic.index) */
    @JvmField
    val data: Vector<DataInput>,
    characteristics: Vector<CharacteristicData>
) : Bluetooth(idString, deviceName, deviceAddress, uuidFilter, autoConnect, activity, context, characteristics) {

    private val requestedTriggers = HashSet<String>()

    fun requestSend(triggerId: String) {
        requestedTriggers.add(triggerId)
    }

    /**
     * Assemble and write the data of each mapped characteristic. Writes are queued with
     * coalescing: if the previous value for a characteristic has not been transmitted yet (slow
     * connection), it is replaced by the new one, so the device always receives the newest data.
     */
    fun sendData() {
        if (forcedBreak)
            return

        for ((characteristic, characteristicList) in mapping) {
            var n = 0
            var out: ByteArray? = null
            for (c in characteristicList) {
                val triggerId = c.triggerId
                if (!triggerId.isNullOrEmpty() && !requestedTriggers.contains(triggerId))
                    continue

                if (data[c.index].filledSize != 0) {
                    val value = convertData(data[c.index].buffer, c.outputConversionFunction)
                    val offset = c.outputOffset.toInt()
                    if (value.size + offset > n) {
                        n = value.size + offset
                        val newOut = ByteArray(n)
                        out?.copyInto(newOut)
                        out = newOut
                    }
                    value.copyInto(out!!, offset)
                    if (!data[c.index].keep)
                        data[c.index].clear(false)
                }
            }
            if (out != null) {
                submitDataWrite(characteristic.uuid, out)
            }
        }
        requestedTriggers.clear()
    }

    /**
     * Convert data using the specified conversion function. Returns an empty byte array in case
     * of an exception.
     */
    private fun convertData(data: DataBuffer?, conversionFunction: ConversionsOutput.OutputConversion?): ByteArray {
        return try {
            conversionFunction?.convert(data) ?: ByteArray(0)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
}
