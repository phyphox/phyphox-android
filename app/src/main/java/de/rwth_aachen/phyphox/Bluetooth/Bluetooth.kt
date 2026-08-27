package de.rwth_aachen.phyphox.Bluetooth

import android.app.Activity
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import de.rwth_aachen.phyphox.Experiment
import de.rwth_aachen.phyphox.ExperimentTimeReference
import de.rwth_aachen.phyphox.PhyphoxFile
import de.rwth_aachen.phyphox.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Serializable
import java.util.UUID
import java.util.Vector
import kotlin.math.min

/**
 * The Bluetooth class encapsulates a generic Bluetooth Low Energy connection and deals with
 * connecting a device, operating on its characteristics, error reporting and reconnecting.
 *
 * All GATT operations go through an event driven [BleCommandQueue]: exactly one operation is in
 * flight at any time, each operation has a timeout, and the completion is matched to the
 * operation by the GATT callback. A lost callback (flaky device, poor signal) therefore fails a
 * single operation instead of stalling the engine, and repeated timeouts are treated as a lost
 * connection which triggers the reconnect logic (endless retries with exponential backoff while
 * the experiment is running).
 */
open class Bluetooth(
    @JvmField var idString: String?,
    deviceName: String?,
    @JvmField var deviceAddress: String?,
    @JvmField var uuidFilter: UUID?,
    @JvmField var autoConnect: Boolean,
    @JvmField protected val activity: Activity,
    @JvmField protected val context: Context,
    /** holds data to all characteristics to add or configure once the device is connected */
    @JvmField protected val characteristics: Vector<CharacteristicData>
) : Serializable {

    @JvmField
    var deviceName: String = deviceName ?: ""

    @JvmField
    var requestMTU: Int = 0

    @Transient
    @JvmField
    protected var btDevice: BluetoothDevice? = null

    @Transient
    @JvmField
    protected var btGatt: BluetoothGatt? = null

    @Transient
    private var eventCharacteristic: BluetoothGattCharacteristic? = null

    /** number of values from characteristics that should be written */
    @JvmField
    protected var valuesSize = 0

    /** maps all characteristics that have extra=time to the index of the buffer */
    @Transient
    @JvmField
    protected var saveTime = HashMap<BluetoothGattCharacteristic, Int>()

    /** each BluetoothGattCharacteristic that should be read or written maps to a list of Characteristics */
    @Transient
    @JvmField
    protected var mapping = HashMap<BluetoothGattCharacteristic, ArrayList<Characteristic>>()

    /** indicates whether the experiment is running */
    @Volatile
    @JvmField
    protected var isRunning = false

    /** true if the experiment is running but the device disconnected */
    @Volatile
    @JvmField
    protected var forcedBreak = false

    @Transient
    @JvmField
    protected var mainHandler: Handler = Handler(context.mainLooper)

    @Transient
    @JvmField
    protected var toast: Toast? = null

    @Transient
    private var queue: BleCommandQueue? = null

    /** completed by onConnectionStateChange while a connection attempt is awaited */
    @Transient
    private var connectionEvent: CompletableDeferred<Boolean>? = null

    /** true while this object's own GATT client holds a connection (kept up to date by the callback) */
    @Transient
    @Volatile
    private var gattConnected = false

    /** true once the services of the current connection have been discovered */
    @Transient
    @Volatile
    private var servicesDiscovered = false

    @Transient
    private var reconnectJob: Job? = null

    /** scope for this device's background jobs (reconnect); lives outside the shared BLE thread */
    @Transient
    private var deviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Transient
    private var batteryCharacteristic: BluetoothGattCharacteristic? = null

    private val connectedDeviceInformation = ConnectedDeviceInfo()

    /** timestamp of the last error toast, to throttle repeated errors while running */
    @Transient
    private var lastToastShown = 0L

    /** timestamp of the last RSSI request, to limit the UI update rate */
    @Transient
    private var lastRssiRequest = 0L

    init {
        mainHandler.post {
            toast = Toast.makeText(context, context.resources.getString(R.string.bt_default_error_message), Toast.LENGTH_LONG)
        }
    }

    /**
     * Return true if this object holds a usable connection to the device, that is: its own GATT
     * client is connected and the services of that connection have been discovered.
     *
     * This deliberately does not ask the BluetoothManager which devices are connected on the GATT
     * profile, which is what it used to do. That list is system wide, so it answers "does anybody
     * hold a link to this device" - a different question, and the wrong one for every caller here:
     * another app's link gives this object no characteristics to read, and a link this object
     * opened and then lost is not made usable by somebody else still holding one.
     */
    open fun isConnected(): Boolean {
        if (btAdapter == null || btAdapter?.isEnabled != true)
            return false
        return btGatt != null && gattConnected && servicesDiscovered
    }

    /**
     * Connect with the device. Blocking, must be called from a background thread (it is called
     * from ConnectBluetoothTask and the reconnect job). Throws BluetoothException on any error
     * during device search, connection, service discovery or characteristic configuration.
     */
    @Throws(BluetoothException::class)
    open fun connect(knownDevices: Map<String, BluetoothDevice>?) {
        var reusedDevice = false
        if (btDevice == null) {
            reusedDevice = findDevice(knownDevices)
        }
        if (btDevice == null)
            return //user aborted the scan dialog

        if (btGatt == null || !isConnected()) {
            openConnection()
        }

        eventCharacteristic = try {
            findCharacteristic(phyphoxEventCharacteristicUUID)
        } catch (e: BluetoothException) {
            //That's ok. Most devices do not have a phyphox event characteristic, in which case
            // phyphox simply will not report events.
            null
        }
        if (eventCharacteristic != null && !reusedDevice)
            writeEventCharacteristic(null)

        mapping.clear() //clear mapping so it won't contain a characteristic twice
        saveTime.clear()
        valuesSize = 0
        for (cd in characteristics) {
            cd.process(this)
        }
    }

    /**
     * Search for the device with the specified name or address among devices already known from
     * this experiment (same idString), the paired devices, or by scanning.
     *
     * @return true if a device instance from a previous connection of this experiment was reused
     */
    @Throws(BluetoothException::class)
    protected fun findDevice(knownDevices: Map<String, BluetoothDevice>?): Boolean {
        if (!isEnabled()) {
            throw BluetoothException(context.resources.getString(R.string.bt_exception_disabled), this)
        }

        //First check if we have already connected to a device with the same idString
        if (!idString.isNullOrEmpty() && knownDevices != null && knownDevices.containsKey(idString)) {
            btDevice = knownDevices[idString]
            return true
        }

        //Paired devices get precedence
        for (d in getPairedDevices()) {
            if (deviceName.isNotEmpty() && deviceAddress.isNullOrEmpty()) {
                if (d.name?.contains(deviceName) == true) {
                    btDevice = d
                    break
                }
            } else if (d.address == deviceAddress) {
                btDevice = d
                break
            }
        }
        if (btDevice == null && !deviceAddress.isNullOrEmpty()) {
            btDevice = btAdapter?.getRemoteDevice(deviceAddress)
        }
        if (btDevice == null) {
            //No matching device found - scan for unpaired devices and present possible matches
            // to the user (blocks this background thread until a device is picked or the dialog
            // is cancelled)
            val adapter = btAdapter ?: throw BluetoothException(context.resources.getString(R.string.bt_exception_disabled), this)
            val bsd = BluetoothScanDialog(autoConnect, activity, context, adapter)
            if (!bsd.scanPermission())
                return false
            if (!bsd.locationEnabled())
                return false
            val bdi = bsd.getBluetoothDevice(deviceName, uuidFilter, null, null, idString)
            if (bdi != null)
                btDevice = bdi.device
        }
        if (btDevice == null) {
            throw BluetoothException(context.resources.getString(R.string.bt_exception_notfound), this)
        }
        return false
    }

    /**
     * Connect to the GATT server, negotiate the MTU if requested and discover the services.
     * Each step is awaited with a timeout; a failure throws a BluetoothException with the same
     * user facing messages as the old implementation.
     */
    @Throws(BluetoothException::class)
    protected fun openConnection() {
        if (!isEnabled()) {
            btGatt?.close()
            btGatt = null
            throw BluetoothException(context.resources.getString(R.string.bt_exception_disabled), this)
        }

        closeGattOnly() //make sure a previous (half open) connection does not linger
        queue?.shutdown()
        queue = BleCommandQueue(gattIo, bleScope) { onLinkDead() }

        val connected = CompletableDeferred<Boolean>()
        connectionEvent = connected
        btGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            btDevice?.connectGatt(context, false, btLeGattCallback, BluetoothDevice.TRANSPORT_LE)
        else
            btDevice?.connectGatt(context, false, btLeGattCallback)

        val result = btGatt != null && runBlocking {
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } == true
        }
        connectionEvent = null
        if (!result) {
            btGatt?.close()
            btGatt = null
            throw BluetoothException(context.resources.getString(R.string.bt_exception_connection), this)
        }

        if (requestMTU > 0) {
            val mtuResult = runBlocking { queue!!.run(BleOp.RequestMtu(requestMTU)) }
            if (!mtuResult.ok || mtuResult.mtu < requestMTU) {
                throw BluetoothException("Could not set MTU as requested by the experiment configuration.", this)
            }
        }

        val discovered = runBlocking { queue!!.run(BleOp.DiscoverServices()) }
        if (!discovered.ok) {
            throw BluetoothException(context.resources.getString(R.string.bt_exception_services), this)
        }
        servicesDiscovered = true

        //Read the battery level for the connected-device info if the device offers it (optional,
        // guarded - not every device has a battery service)
        batteryCharacteristic = btGatt?.getService(BATTERY_UUID)?.getCharacteristic(BATTERY_LEVEL)
        batteryCharacteristic?.let { requestBatteryLevel(it) }
    }

    /**
     * Close the connection to the GATT server of the device.
     */
    open fun closeConnection() {
        reconnectJob?.cancel()
        reconnectJob = null
        closeGattOnly()
        queue?.shutdown()
        queue = null
    }

    private fun closeGattOnly() {
        connectionEvent?.complete(false)
        gattConnected = false
        servicesDiscovered = false
        btGatt?.close()
        btGatt = null
        queue?.clear()
    }

    /**
     * Called when the experiment is started.
     */
    @Throws(BluetoothException::class)
    open fun start() {
        if (!isConnected()) {
            throw BluetoothException(context.resources.getString(R.string.bt_exception_no_connection), this)
        }
        forcedBreak = false
        isRunning = true
        startAcquisition()
    }

    /**
     * Called when the experiment is stopped.
     */
    open fun stop() {
        isRunning = false
        stopAcquisition()
        reconnectJob?.cancel()
        reconnectJob = null
        mainHandler.post { toast?.cancel() }
        queue?.clear()
    }

    /**
     * Subclass hook: begin data acquisition (subscribe to notifications, start polling...).
     * Also called after a successful reconnect while the experiment keeps running.
     */
    @Throws(BluetoothException::class)
    protected open fun startAcquisition() {
    }

    /**
     * Subclass hook: end data acquisition without changing the running state. Also called when
     * the connection breaks down while the experiment keeps running.
     */
    protected open fun stopAcquisition() {
    }

    /**
     * Called when there was a notification that the value of a characteristic has changed.
     */
    protected open fun retrieveData(data: ByteArray, characteristic: BluetoothGattCharacteristic) {
    }

    /**
     * Called with the result of a queued characteristic read (data is null if the read failed).
     */
    protected open fun saveData(data: ByteArray?, characteristic: BluetoothGattCharacteristic) {
    }

    /**
     * Searches the connected device for the specified characteristic.
     */
    @Throws(BluetoothException::class)
    fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic {
        val services = btGatt?.services ?: emptyList()
        for (service in services) {
            for (c in service.characteristics) {
                if (uuid == c.uuid)
                    return c
            }
        }
        throw BluetoothException(context.resources.getString(R.string.bt_exception_uuid) + " " + uuid.toString() + " " + context.resources.getString(R.string.bt_exception_uuid2), this)
    }

    //
    // Queue access for subclasses and CharacteristicData
    //

    /** Enqueue a write and await its completion. Used for configuration during connect. */
    @Throws(BluetoothException::class)
    internal fun awaitWrite(characteristic: UUID, value: ByteArray) {
        val q = queue ?: throw BluetoothException(context.resources.getString(R.string.bt_exception_no_connection), this)
        val result = runBlocking { q.run(BleOp.Write(characteristic, value)) }
        if (!result.ok) {
            throw BluetoothException(context.resources.getString(R.string.bt_fail_writing), this)
        }
    }

    /** Enqueue a descriptor write and await its completion (notification subscriptions). */
    internal fun awaitWriteDescriptor(characteristic: UUID, descriptor: UUID, value: ByteArray): Boolean {
        val q = queue ?: return false
        return runBlocking { q.run(BleOp.WriteDescriptor(characteristic, descriptor, value)) }.ok
    }

    /** Enqueue a data write without waiting; a newer value replaces a queued one. */
    internal fun submitDataWrite(characteristic: UUID, value: ByteArray) {
        queue?.enqueue(BleOp.Write(characteristic, value, coalescible = true))
    }

    /** Enqueue a control write without waiting (event characteristic). */
    internal fun submitControlWrite(characteristic: UUID, value: ByteArray) {
        queue?.enqueue(BleOp.Write(characteristic, value))
    }

    /** Enqueue a read; the result is passed to saveData. Duplicate pending reads coalesce. */
    internal fun submitRead(characteristic: BluetoothGattCharacteristic) {
        val q = queue ?: return
        val deferred = q.enqueue(BleOp.Read(characteristic.uuid))
        bleScope.launch {
            val result = deferred.await()
            if (result.status == BleResult.Status.CANCELLED)
                return@launch
            saveData(if (result.ok) result.value else null, characteristic)
        }
    }

    private fun requestBatteryLevel(characteristic: BluetoothGattCharacteristic) {
        val q = queue ?: return
        val deferred = q.enqueue(BleOp.Read(characteristic.uuid))
        bleScope.launch {
            val result = deferred.await()
            val value = result.value
            if (result.ok && value != null && value.isNotEmpty()) {
                connectedDeviceInformation.batteryLabel = value[0].toInt() and 0xff
            }
        }
    }

    //
    // Error handling
    //

    /**
     * Display the error message (as toast if the experiment is running, as dialog if not).
     */
    protected fun displayErrorMessage(message: String?) {
        displayErrorMessage(message, !isRunning)
    }

    protected fun displayErrorMessage(message: String?, showDialog: Boolean) {
        if (showDialog) {
            errorDialog.message = message ?: ""
            mainHandler.post(errorDialog)
        } else {
            //While the experiment is running errors are shown as a toast, throttled so a
            // reconnect loop cannot spam the user
            val now = System.currentTimeMillis()
            if (now - lastToastShown < TOAST_THROTTLE_MS)
                return
            lastToastShown = now
            mainHandler.post {
                toast?.setText(message ?: context.resources.getString(R.string.bt_default_error_message))
                toast?.show()
            }
        }
    }

    /**
     * Called by the queue when several operations in a row timed out: the device is most likely
     * gone without a disconnect callback. Treat it like a disconnect.
     */
    private fun onLinkDead() {
        if (isRunning) {
            handleDisconnect()
        }
    }

    /**
     * The connection broke down. If the experiment is running, pause the acquisition and retry
     * with exponential backoff until the device is back or the experiment is stopped.
     *
     * May be called from GATT binder threads and from the queue's own worker thread (link-dead
     * detection), so everything that could block on a queue operation runs on the device scope.
     */
    private fun handleDisconnect() {
        queue?.clear()
        synchronized(this) {
            if (!isRunning || reconnectJob?.isActive == true)
                return
            forcedBreak = true
            reconnectJob = deviceScope.launch {
                stopAcquisition()
                displayErrorMessage(context.resources.getString(R.string.bt_exception_disconnected) + BluetoothException.getMessage(this@Bluetooth), false)

                var backoff = RECONNECT_INITIAL_BACKOFF_MS
                while (isActive && isRunning) {
                    delay(backoff)
                    try {
                        connect(null) //reuses btDevice, reopens the GATT connection and reconfigures
                        if (isRunning) {
                            forcedBreak = false
                            startAcquisition()
                            mainHandler.post { toast?.cancel() }
                        }
                        return@launch
                    } catch (e: BluetoothException) {
                        displayErrorMessage(e.message, false)
                        backoff = min(backoff * 2, RECONNECT_MAX_BACKOFF_MS)
                    }
                }
            }
        }
    }

    //
    // GATT plumbing
    //

    /** Executes queue operations on the actual BluetoothGatt (shared with other queue users, see [BleGattIo]) */
    @Transient
    private val gattIo = BleGattIo { btGatt }

    /**
     * The single GATT callback: feeds operation results into the queue and dispatches
     * notifications to the data path.
     */
    @Transient
    private val btLeGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            //A callback of a GATT client that has already been replaced must not touch the state
            //of the current one. btGatt is still null while a fresh attempt is in flight (the
            //callback can fire before connectGatt has returned), so that case has to pass.
            val current = btGatt
            if (current != null && current !== gatt)
                return

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gattConnected = status == BluetoothGatt.GATT_SUCCESS
                    connectedDeviceInformation.deviceId = gatt.device.address
                    connectedDeviceInformation.deviceName = gatt.device.name
                    connectionEvent?.complete(status == BluetoothGatt.GATT_SUCCESS)
                }
                else -> {
                    //STATE_DISCONNECTED and everything unexpected
                    gattConnected = false
                    servicesDiscovered = false
                    connectionEvent?.complete(false)
                    if (isRunning) {
                        handleDisconnect()
                    }
                }
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            //Update RSSI/battery info for the connected-device UI at a limited rate
            val now = System.currentTimeMillis()
            if (Experiment.isBluetoothConnectionSuccessful && now - lastRssiRequest >= RSSI_INTERVAL_MS) {
                lastRssiRequest = now
                queue?.enqueue(BleOp.ReadRssi())?.let { deferred ->
                    bleScope.launch {
                        val result = deferred.await()
                        if (result.ok)
                            updateConnectedDeviceInfo(result.rssi)
                        batteryCharacteristic?.let { requestBatteryLevel(it) }
                    }
                }
            }

            val data = characteristic.value ?: return
            retrieveData(data, characteristic)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            queue?.onEvent(
                BleEvent.CharacteristicRead(
                    characteristic.uuid,
                    if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null,
                    status == BluetoothGatt.GATT_SUCCESS
                )
            )
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS && isRunning) {
                displayErrorMessage(context.resources.getString(R.string.bt_fail_writing) + BluetoothException.getMessage(this@Bluetooth), false)
            }
            queue?.onEvent(BleEvent.CharacteristicWritten(characteristic.uuid, status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            queue?.onEvent(BleEvent.DescriptorWritten(descriptor.characteristic.uuid, descriptor.uuid, status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            queue?.onEvent(BleEvent.ServicesDiscovered(status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            queue?.onEvent(BleEvent.MtuChanged(mtu, status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            queue?.onEvent(BleEvent.RssiRead(rssi, status == BluetoothGatt.GATT_SUCCESS))
        }
    }

    private fun updateConnectedDeviceInfo(rssi: Int) {
        connectedDeviceInformation.signalStrength = rssi
        //Each device only reports its own info. The Experiment activity merges the reports of
        // all connected devices into the list shown at the bottom of the screen.
        mainHandler.post {
            Experiment.updateConnectedDeviceDelegate?.updateConnectedDevice(arrayListOf(connectedDeviceInformation))
        }
    }

    // Writes the status and time references (experiment time and system time) when triggered by
    // a start/pause event.
    fun writeEventCharacteristic(timeMapping: ExperimentTimeReference.TimeMapping?) {
        val eventChar = eventCharacteristic
        if (forcedBreak || eventChar == null)
            return
        val out = ByteArray(17)

        //Byte 0 is 0x00 for pause, 0x01 for start, 0x02 for clear, 0xff for "connection established"
        out[0] = when (timeMapping?.event) {
            ExperimentTimeReference.TimeMappingEvent.PAUSE -> 0x00
            ExperimentTimeReference.TimeMappingEvent.START -> 0x01
            ExperimentTimeReference.TimeMappingEvent.CLEAR -> 0x02
            null -> 0xff.toByte()
        }

        //Bytes 1-8 are experiment time in millis since experiment start as a 64 bit signed
        // integer (big endian to match the endianness of existing characteristics and Java's
        // system time format). -1 if no measurement has run yet.
        val experimentTimeMillis = if (timeMapping != null) (timeMapping.experimentTime * 1000).toLong() else -1L
        for (i in 0 until 8)
            out[1 + i] = (experimentTimeMillis shr (56 - 8 * i)).toByte()

        //Bytes 9-16 are system time in millis since 1970 (same format)
        val systemTimeMillis = timeMapping?.systemTime ?: System.currentTimeMillis()
        for (i in 0 until 8)
            out[9 + i] = (systemTimeMillis shr (56 - 8 * i)).toByte()

        submitControlWrite(eventChar.uuid, out)
    }

    /**
     * Represents the attributes of a characteristic as they are defined in the phyphox file.
     */
    class Characteristic {
        /** Index of the buffer the characteristic value should be saved in / read from */
        @JvmField
        val index: Int

        @JvmField
        var triggerId: String? = null

        @JvmField
        var configConversionFunction: ConversionsConfig.ConfigConversion? = null

        @JvmField
        var inputConversionFunction: ConversionsInput.InputConversion? = null

        @JvmField
        var outputConversionFunction: ConversionsOutput.OutputConversion? = null

        @JvmField
        var outputOffset: Short = 0

        constructor(index: Int, conversionFunction: ConversionsInput.InputConversion?) {
            this.index = index
            this.inputConversionFunction = conversionFunction
        }

        constructor(index: Int, conversionFunction: ConversionsOutput.OutputConversion?, outputOffset: Short, triggerId: String?) {
            this.index = index
            this.outputConversionFunction = conversionFunction
            this.outputOffset = outputOffset
            this.triggerId = triggerId
        }
    }

    /**
     * Holds data of a characteristic that was collected from the phyphox file.
     */
    abstract class CharacteristicData(@JvmField val uuid: UUID) : Serializable {
        /**
         * Called once the connection is established to add the characteristic to the Bluetooth
         * object (or to write its configuration).
         */
        @Throws(BluetoothException::class)
        abstract fun process(b: Bluetooth)
    }

    /**
     * A characteristic the values of which are recorded by a BluetoothInput.
     */
    class InputData(uuid: UUID, @JvmField val extraTime: Boolean, @JvmField val index: Int, conversionFunction: ConversionsInput.InputConversion?) : CharacteristicData(uuid), Serializable {
        @JvmField
        val conversionFunction: ConversionsInput.InputConversion? = if (extraTime) null else conversionFunction

        @Throws(BluetoothException::class)
        override fun process(b: Bluetooth) {
            val c = b.findCharacteristic(uuid)
            if (extraTime) {
                b.saveTime[c] = index
            } else {
                b.mapping.getOrPut(c) { ArrayList() }.add(Characteristic(index, conversionFunction))
                b.valuesSize++
            }
        }
    }

    /**
     * A characteristic that is written with buffer data by a BluetoothOutput.
     */
    class OutputData(uuid: UUID, @JvmField val index: Int, @JvmField val conversionFunction: ConversionsOutput.OutputConversion?, @JvmField val offset: Short, @JvmField val triggerId: String?) : CharacteristicData(uuid), Serializable {

        @Throws(BluetoothException::class)
        override fun process(b: Bluetooth) {
            val c = b.findCharacteristic(uuid)
            b.mapping.getOrPut(c) { ArrayList() }.add(Characteristic(index, conversionFunction, offset, triggerId))
            b.valuesSize++
        }
    }

    /**
     * A characteristic that receives a fixed configuration value when the connection is set up.
     */
    class ConfigData : CharacteristicData, Serializable {
        @JvmField
        val value: ByteArray

        @Throws(PhyphoxFile.phyphoxFileException::class)
        constructor(uuid: UUID, data: String, conversionFunction: ConversionsConfig.ConfigConversion) : super(uuid) {
            try {
                this.value = conversionFunction.convert(data)
            } catch (e: Exception) { //catch any exception that occurs in the conversion function
                throw PhyphoxFile.phyphoxFileException("An error occurred on the conversion function \"" + conversionFunction.javaClass.name + "\". ")
            }
        }

        @Throws(BluetoothException::class)
        override fun process(b: Bluetooth) {
            val c = b.findCharacteristic(uuid)
            b.awaitWrite(c.uuid, value)
        }
    }

    /**
     * Thrown to indicate that there was an error concerning Bluetooth.
     */
    class BluetoothException(message: String, b: Bluetooth) : Exception(message + getMessage(b)) {
        companion object {
            /**
             * Return a String with the device data (address and name if not null).
             */
            @JvmStatic
            fun getMessage(b: Bluetooth): String {
                var message = System.lineSeparator() + b.context.resources.getString(R.string.bt_exception_device)
                if (b.deviceAddress != null) {
                    message += " " + b.context.resources.getString(R.string.bt_exception_device_address) + " \"" + b.deviceAddress + "\""
                }
                message += " " + b.context.resources.getString(R.string.bt_exception_device_name) + " \"" + b.deviceName + "\""
                message += "."
                return message
            }
        }
    }

    /**
     * Runnable that displays an AlertDialog with an error message and the option to try again.
     */
    class OnExceptionRunnable : Runnable {
        @JvmField
        var message: String = ""

        @JvmField
        var context: Context? = null

        @JvmField
        var tryAgain: Runnable? = null

        @JvmField
        var cancel: Runnable? = null

        override fun run() {
            val ctx = context ?: return
            if (message.isEmpty()) {
                message = ctx.resources.getString(R.string.bt_default_error_message)
            }
            val ctw = ContextThemeWrapper(ctx, R.style.Theme_Phyphox_DayNight)
            val builder = AlertDialog.Builder(ctw)
            val neLayout = (ctw.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.error_dialog, null)
            builder.setView(neLayout)
            neLayout.findViewById<TextView>(R.id.errorText).text = message
            tryAgain?.let { retry ->
                builder.setPositiveButton(ctx.resources.getString(R.string.tryagain)) { _, _ -> retry.run() }
            }
            builder.setNegativeButton(ctx.resources.getString(R.string.cancel)) { _, _ -> cancel?.run() }
            builder.create().show()
        }
    }

    /**
     * Connects all Bluetooth devices of an experiment on a background thread, showing a progress
     * dialog meanwhile and the error dialog (with try again option) on failure. Successor of the
     * old AsyncTask with the same interface towards Experiment.
     */
    class ConnectBluetoothTask {
        @JvmField
        var progress: ProgressDialog? = null

        @JvmField
        var onSuccess: Runnable? = null

        fun execute(vararg params: Vector<out Bluetooth>) {
            Thread {
                var errorMessage: String? = null
                outer@ for (v in params) {
                    for (b in v) {
                        try {
                            b.queue?.clear() //there could be remains from a previous attempt
                            b.connect(knownDevicesFromIO(*params))
                        } catch (e: BluetoothException) {
                            b.displayErrorMessage(e.message, true)
                            errorMessage = e.message
                            break@outer
                        }
                    }
                }
                val result = errorMessage
                Handler(Looper.getMainLooper()).post {
                    progress?.hide() //don't dismiss yet, "try again" might need it
                    if (result == null) {
                        progress?.dismiss()
                        onSuccess?.run()
                    }
                }
            }.start()
        }
    }

    companion object {
        @JvmField
        val baseUUID: UUID = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb")

        @JvmField
        val phyphoxServiceUUID: UUID = UUID.fromString("cddf0001-30f7-4671-8b43-5e40ba53514a")

        @JvmField
        val phyphoxExperimentCharacteristicUUID: UUID = UUID.fromString("cddf0002-30f7-4671-8b43-5e40ba53514a")

        @JvmField
        val phyphoxExperimentControlCharacteristicUUID: UUID = UUID.fromString("cddf0003-30f7-4671-8b43-5e40ba53514a")

        @JvmField
        val phyphoxEventCharacteristicUUID: UUID = UUID.fromString("cddf0004-30f7-4671-8b43-5e40ba53514a")

        private val BATTERY_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        const val CONNECT_TIMEOUT_MS = 10000L
        const val RSSI_INTERVAL_MS = 1000L
        const val TOAST_THROTTLE_MS = 5000L
        const val RECONNECT_INITIAL_BACKOFF_MS = 1000L
        const val RECONNECT_MAX_BACKOFF_MS = 30000L

        @JvmField
        var errorDialog = OnExceptionRunnable()

        internal var btAdapter: BluetoothAdapter? = null

        /** The shared thread all queue workers and light-weight engine jobs run on */
        private val bleThread by lazy {
            HandlerThread("phyphoxBLE").also { it.start() }
        }

        internal val bleScope by lazy {
            CoroutineScope(SupervisorJob() + Handler(bleThread.looper).asCoroutineDispatcher("phyphoxBLE"))
        }

        /**
         * Return true if Bluetooth Low Energy is supported on the device.
         */
        @JvmStatic
        fun isSupported(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        }

        /**
         * Return true if Bluetooth is enabled on the device.
         */
        @JvmStatic
        fun isEnabled(): Boolean {
            if (btAdapter == null) {
                btAdapter = BluetoothAdapter.getDefaultAdapter()
            }
            return btAdapter?.isEnabled == true
        }

        /**
         * Return a list of all paired Bluetooth Low Energy devices.
         */
        @JvmStatic
        fun getPairedDevices(): Vector<BluetoothDevice> {
            val result = Vector<BluetoothDevice>()
            for (b in btAdapter?.bondedDevices ?: emptySet()) {
                if (b.type == BluetoothDevice.DEVICE_TYPE_DUAL || b.type == BluetoothDevice.DEVICE_TYPE_LE) {
                    result.add(b)
                }
            }
            return result
        }

        /** Collects already resolved devices by their idString so multiple inputs/outputs referring to the same device reuse it */
        @JvmStatic
        fun knownDevicesFromIO(vararg list: Vector<out Bluetooth>): Map<String, BluetoothDevice> {
            val knownDevices = HashMap<String, BluetoothDevice>()
            for (v in list) {
                for (b in v) {
                    val device = b.btDevice
                    val id = b.idString
                    if (device != null && !id.isNullOrEmpty())
                        knownDevices[id] = device
                }
            }
            return knownDevices
        }
    }
}
