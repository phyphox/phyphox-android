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

/**
 * A Bluetooth Low Energy device that provides measurement data to the experiment, either pushed
 * by the device (mode "notification"/"indication") or read periodically (mode "poll").
 */
class BluetoothInput @Throws(PhyphoxFile.phyphoxFileException::class) constructor(
    idString: String?,
    deviceName: String?,
    deviceAddress: String?,
    mode: String,
    uuidFilter: UUID?,
    autoConnect: Boolean,
    rate: Double,
    private val subscribeOnStart: Boolean,
    /** data buffers the received values are appended to (indexed by Characteristic.index) */
    private val data: Vector<DataOutput>,
    private val dataLock: Lock,
    activity: Activity,
    context: Context,
    characteristics: Vector<CharacteristicData>,
    private val experimentTimeReference: ExperimentTimeReference
) : Bluetooth(idString, deviceName, deviceAddress, uuidFilter, autoConnect, activity, context, characteristics) {

    private val mode: String = mode.lowercase()

    /** acquisition period in nanoseconds (inverse rate), 0 corresponds to as fast as possible */
    private val period: Long

    @Transient
    private var pollJob: Job? = null

    /** collects one set of values per polled characteristic before they are written to the buffers together */
    @Transient
    private var outputs = HashMap<Int, List<Double>>()

    init {
        if (this.mode == "poll" && rate < 0) {
            throw PhyphoxFile.phyphoxFileException(context.resources.getString(R.string.bt_exception_rate))
        }
        period = if (rate <= 0) 0 else ((1 / rate) * 1e9).toLong()
    }

    private val subscribed get() = mode == "notification" || mode == "indication"

    /**
     * Connect and - unless subscribeOnStart is set - enable notifications/indications already
     * now, so slowly delivering sensors show data as soon as possible.
     */
    @Throws(BluetoothException::class)
    override fun connect(knownDevices: Map<String, BluetoothDevice>?) {
        super.connect(knownDevices)

        if (!subscribeOnStart && subscribed) {
            subscribeToNotifications()
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
                    //Read all characteristics; pending duplicates coalesce in the queue, so a
                    // device that answers slower than the requested rate is polled as fast as it
                    // can answer instead of piling up a backlog.
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

    /**
     * Turn on notifications/indications for every mapped characteristic. Each descriptor write
     * is awaited (with the queue timeout); devices without a client characteristic configuration
     * descriptor are tolerated, they might be sending notifications permanently.
     */
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
                //awaited, but a failure is not reported - the connection might be gone already
                awaitWriteDescriptor(c.uuid, CONFIG_DESCRIPTOR, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            }
            btGatt?.setCharacteristicNotification(c, false)
        }
    }

    /**
     * Result of a queued poll read. Collects the converted values and flushes them to the
     * buffers when a value has arrived for every mapped characteristic (or when a characteristic
     * delivers a second value before the set is complete).
     */
    override fun saveData(data: ByteArray?, characteristic: BluetoothGattCharacteristic) {
        val characteristicList = mapping[characteristic] ?: return
        for (c in characteristicList) {
            //flush if the data from this characteristic is already stored
            if (outputs.containsKey(c.index)) {
                flushPolledData()
                return
            }
            if (data != null) {
                outputs[c.index] = convertData(data, c.inputConversionFunction)
            }
        }
        //flush if data from every characteristic has been received
        if (outputs.size == valuesSize) {
            flushPolledData()
        }
    }

    /**
     * A notification arrived: convert and append to the buffers immediately.
     */
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
            //append time to its buffer if extra=time is set for this characteristic
            saveTime[characteristic]?.let { index ->
                this.data[index].append(t)
                this.data[index].markSet()
            }
        } finally {
            dataLock.unlock()
        }
    }

    /**
     * Write one complete set of polled values (and the poll time for extra=time outputs) to the
     * buffers.
     */
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

    /**
     * Convert data using the specified conversion function. Returns an empty list in case of an
     * exception, so a malformed packet never crashes the experiment.
     */
    private fun convertData(data: ByteArray, conversionFunction: ConversionsInput.InputConversion?): List<Double> {
        return try {
            conversionFunction?.convert(data) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        /** UUID of the descriptor for the client characteristic configuration */
        @JvmField
        val CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
