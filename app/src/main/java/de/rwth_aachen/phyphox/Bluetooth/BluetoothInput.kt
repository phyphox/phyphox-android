package de.rwth_aachen.phyphox.Bluetooth

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import de.rwth_aachen.phyphox.DataOutput
import de.rwth_aachen.phyphox.ExperimentTimeReference
import de.rwth_aachen.phyphox.PhyphoxFile
import de.rwth_aachen.phyphox.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.Vector
import java.util.concurrent.locks.Lock

/** A BLE device delivering data to the experiment, pushed (notification/indication) or polled (poll). */
class BluetoothInput @Throws(PhyphoxFile.phyphoxFileException::class) constructor(
    idString: String?,
    deviceName: String?,
    deviceAddress: String?,
    mode: String,
    uuidFilter: UUID?,
    autoConnect: Boolean,
    rate: Double,
    private val subscribeOnStart: Boolean,
    private val data: Vector<DataOutput>,
    private val dataLock: Lock,
    activity: Activity,
    context: Context,
    characteristics: Vector<CharacteristicData>,
    private val experimentTimeReference: ExperimentTimeReference
) : Bluetooth(idString, deviceName, deviceAddress, uuidFilter, autoConnect, activity, context, characteristics) {

    private val mode: String = mode.lowercase()

    private val period: Long //ns, 0 = as fast as possible

    @Transient
    private var pollJob: Job? = null

    @Transient
    private var outputs = HashMap<Int, List<Double>>() //one set of polled values, flushed to the buffers together

    init {
        if (this.mode == "poll" && rate < 0) {
            throw PhyphoxFile.phyphoxFileException(context.resources.getString(R.string.bt_exception_rate))
        }
        period = if (rate <= 0) 0 else ((1 / rate) * 1e9).toLong()
    }

    private val subscribed get() = mode == "notification" || mode == "indication"

    @Throws(BluetoothException::class)
    override fun connect(knownDevices: Map<String, BluetoothDevice>?) {
        super.connect(knownDevices)

        if (!subscribeOnStart && subscribed) {
            subscribeToNotifications() //already now, so slowly delivering sensors show data as soon as possible
        }
    }

    override fun closeConnection() {
        if (!subscribeOnStart && subscribed) {
            unsubscribeFromNotifications()
        }
        super.closeConnection()
    }

    @Throws(BluetoothException::class)
    override fun startAcquisition() {
        outputs = HashMap()

        if (subscribeOnStart && subscribed) {
            subscribeToNotifications()
        }

        if (mode == "poll") {
            val periodMs = (period / 1000000L).coerceAtLeast(1)
            pollJob = bleScope.launch {
                while (isActive) {
                    //duplicate reads coalesce in the queue, so a slow device is polled as fast as it answers
                    for (c in mapping.keys)
                        submitRead(c)
                    delay(periodMs)
                }
            }
        }
    }

    override fun stopAcquisition() {
        pollJob?.cancel()
        pollJob = null

        if (subscribeOnStart && subscribed) {
            unsubscribeFromNotifications()
        }
    }

    @Throws(BluetoothException::class)
    private fun subscribeToNotifications() {
        for (c in mapping.keys) {
            val result = btGatt?.setCharacteristicNotification(c, true) ?: false
            if (!result) {
                throw BluetoothException(context.resources.getString(R.string.bt_exception_notification) + " " + c.uuid.toString() + " " + context.resources.getString(R.string.bt_exception_notification_enable), this)
            }
        }

        for (c in mapping.keys) {
            if (c.getDescriptor(CONFIG_DESCRIPTOR) == null)
                continue //no config descriptor - the device might be notifying permanently

            val properties = c.properties
            val value = if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else if ((properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else {
                Log.e("BLE", "Characteristic properties neither support notify nor indicate. Trying notify anyways.")
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }

            if (!awaitWriteDescriptor(c.uuid, CONFIG_DESCRIPTOR, value)) {
                throw BluetoothException(context.resources.getString(R.string.bt_exception_notification_fail_enable) + " " + c.uuid.toString() + " " + context.resources.getString(R.string.bt_exception_notification_fail), this)
            }
        }
    }

    private fun unsubscribeFromNotifications() {
        for (c in mapping.keys) {
            if (c.getDescriptor(CONFIG_DESCRIPTOR) != null) {
                //a failure is not reported, the connection might be gone already
                awaitWriteDescriptor(c.uuid, CONFIG_DESCRIPTOR, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            }
            btGatt?.setCharacteristicNotification(c, false)
        }
    }

    //poll result: flushed when every characteristic delivered a value or one delivers a second
    override fun saveData(data: ByteArray?, characteristic: BluetoothGattCharacteristic) {
        val characteristicList = mapping[characteristic] ?: return
        for (c in characteristicList) {
            if (outputs.containsKey(c.index)) {
                flushPolledData()
                return
            }
            if (data != null) {
                outputs[c.index] = convertData(data, c.inputConversionFunction)
            }
        }
        if (outputs.size == valuesSize) {
            flushPolledData()
        }
    }

    override fun retrieveData(data: ByteArray, characteristic: BluetoothGattCharacteristic) {
        if (!isRunning)
            return //Experiment has not started yet. Discard early events.

        val characteristicList = mapping[characteristic] ?: return
        val t = experimentTimeReference.experimentTime

        val converted = characteristicList.map { convertData(data, it.inputConversionFunction) }

        dataLock.lock()
        try {
            for ((i, c) in characteristicList.withIndex()) {
                for (v in converted[i])
                    this.data[c.index].append(v)
                this.data[c.index].markSet()
            }
            saveTime[characteristic]?.let { index -> //extra=time
                this.data[index].append(t)
                this.data[index].markSet()
            }
        } finally {
            dataLock.unlock()
        }
    }

    private fun flushPolledData() {
        val t = experimentTimeReference.experimentTime

        dataLock.lock()
        try {
            for (characteristicList in mapping.values) {
                for (c in characteristicList) {
                    for (v in outputs[c.index] ?: emptyList())
                        data[c.index].append(v)
                    data[c.index].markSet()
                }
            }
            for (index in saveTime.values) {
                data[index].append(t)
                data[index].markSet()
            }
        } finally {
            dataLock.unlock()
            outputs.clear()
        }
    }

    private fun convertData(data: ByteArray, conversionFunction: ConversionsInput.InputConversion?): List<Double> {
        return try {
            conversionFunction?.convert(data) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        @JvmField
        val CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") //client characteristic configuration
    }
}
