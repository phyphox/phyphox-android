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
import android.os.SystemClock
import android.util.Log
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
 * A generic BLE connection: device search, connect, characteristic setup, error reporting and
 * reconnect (exponential backoff while running). All GATT operations go through a [BleCommandQueue].
 */
open class Bluetooth(
    @JvmField var idString: String?,
    deviceName: String?,
    @JvmField var deviceAddress: String?,
    @JvmField var uuidFilter: UUID?,
    @JvmField var autoConnect: Boolean,
    @JvmField protected val activity: Activity,
    @JvmField protected val context: Context,
    @JvmField protected val characteristics: Vector<CharacteristicData>
) : Serializable {

    @JvmField
    var deviceName: String = deviceName ?: ""

    @JvmField
    var requestMTU: Int = 0

    //blocks with the same id share the first block's connection, the owner holds all connection state (see shareConnections)
    @Transient
    private var owner: Bluetooth = this

    @Transient
    private var sharing: MutableList<Bluetooth> = mutableListOf(this) //this one first, maintained on the owner only

    private val ownsConnection get() = owner === this

    @Transient
    private var ownDevice: BluetoothDevice? = null

    @Transient
    private var ownGatt: BluetoothGatt? = null

    protected var btDevice: BluetoothDevice?
        get() = owner.ownDevice
        set(value) { owner.ownDevice = value }

    protected var btGatt: BluetoothGatt?
        get() = owner.ownGatt
        set(value) { owner.ownGatt = value }

    @Transient
    private var eventCharacteristic: BluetoothGattCharacteristic? = null

    @JvmField
    protected var valuesSize = 0 //number of mapped Characteristics

    @Transient
    @JvmField
    protected var saveTime = HashMap<BluetoothGattCharacteristic, Int>() //extra=time characteristic -> buffer index

    @Transient
    @JvmField
    protected var mapping = HashMap<BluetoothGattCharacteristic, ArrayList<Characteristic>>()

    @Volatile
    @JvmField
    protected var isRunning = false

    @Volatile
    @JvmField
    protected var forcedBreak = false //running, but the device disconnected

    @Transient
    @JvmField
    protected var mainHandler: Handler = Handler(context.mainLooper)

    @Transient
    @JvmField
    protected var toast: Toast? = null

    @Transient
    private var ownQueue: BleCommandQueue? = null

    private var queue: BleCommandQueue?
        get() = owner.ownQueue
        set(value) { owner.ownQueue = value }

    @Transient
    private var connectionEvent: CompletableDeferred<Boolean>? = null //completed by onConnectionStateChange

    @Transient
    @Volatile
    private var lastConnectionStatus = 0

    @Transient
    @Volatile
    private var ownGattConnected = false

    @Transient
    @Volatile
    private var ownServicesDiscovered = false

    private var gattConnected: Boolean
        get() = owner.ownGattConnected
        set(value) { owner.ownGattConnected = value }

    private var servicesDiscovered: Boolean
        get() = owner.ownServicesDiscovered
        set(value) { owner.ownServicesDiscovered = value }

    @Transient
    private var reconnectJob: Job? = null

    @Transient
    private var deviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) //reconnect jobs, off the shared BLE thread

    @Transient
    private var batteryCharacteristic: BluetoothGattCharacteristic? = null

    private val connectedDeviceInformation = ConnectedDeviceInfo()

    @Transient
    private var lastToastShown = 0L

    @Transient
    private var lastRssiRequest = 0L

    init {
        mainHandler.post {
            toast = Toast.makeText(context, context.resources.getString(R.string.bt_default_error_message), Toast.LENGTH_LONG)
        }
    }

    //Deliberately not BluetoothManager.getConnectedDevices(): that list is system wide and says nothing
    // about whether this GATT client holds a usable link
    open fun isConnected(): Boolean {
        if (btAdapter == null || btAdapter?.isEnabled != true)
            return false
        return btGatt != null && gattConnected && servicesDiscovered
    }

    //Blocking, must be called from a background thread
    @Throws(BluetoothException::class)
    open fun connect(knownDevices: Map<String, BluetoothDevice>?) {
        var reusedDevice = false
        if (ownsConnection) {
            if (btDevice == null) {
                reusedDevice = findDevice(knownDevices)
            }
            if (btDevice == null)
                return //user aborted the scan dialog

            if (btGatt == null || !isConnected()) {
                openConnection()
            }
        } else {
            //another block with the same id owns the connection and is connected first; only this
            //block's characteristics still have to be set up on it
            if (btDevice == null)
                return //the owner's scan dialog was cancelled
            if (!isConnected())
                throw BluetoothException(context.resources.getString(R.string.bt_exception_no_connection), this)
            reusedDevice = true
        }

        eventCharacteristic = try {
            findCharacteristic(phyphoxEventCharacteristicUUID)
        } catch (e: BluetoothException) {
            null //most devices have no event characteristic, phyphox then just does not report events
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

    //Returns true if a device from a previous connection of this experiment (same idString) was reused
    @Throws(BluetoothException::class)
    protected fun findDevice(knownDevices: Map<String, BluetoothDevice>?): Boolean {
        if (!isEnabled()) {
            throw BluetoothException(context.resources.getString(R.string.bt_exception_disabled), this)
        }

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
            //no match: scan and let the user pick (blocks until picked or cancelled)
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

        var result = false
        var attemptsMade = 0
        val connectDeadline = SystemClock.elapsedRealtime() + CONNECT_TOTAL_BUDGET_MS
        for (attempt in 1..CONNECT_ATTEMPTS) {
            attemptsMade = attempt
            val connected = CompletableDeferred<Boolean>()
            connectionEvent = connected
            btGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                btDevice?.connectGatt(context, false, btLeGattCallback, BluetoothDevice.TRANSPORT_LE)
            else
                btDevice?.connectGatt(context, false, btLeGattCallback)

            result = btGatt != null && runBlocking {
                withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } == true
            }
            connectionEvent = null
            if (result) {
                if (attempt > 1)
                    Log.d(TAG, "connected on attempt $attempt")
                break
            }
            //a refused client keeps its stack registration unless closed, and running out of those causes 133s
            Log.w(TAG, "connect attempt $attempt of $CONNECT_ATTEMPTS failed (status $lastConnectionStatus)")
            btGatt?.close()
            btGatt = null
            if (attempt >= CONNECT_ATTEMPTS ||
                    SystemClock.elapsedRealtime() + CONNECT_RETRY_DELAY_MS >= connectDeadline)
                break
            runBlocking { delay(CONNECT_RETRY_DELAY_MS) }
        }
        reportBleOutcome(TAG, "connect", attemptsMade, result,
                reason = if (result) null else "gatt_$lastConnectionStatus")
        if (!result) {
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

        batteryCharacteristic = btGatt?.getService(BATTERY_UUID)?.getCharacteristic(BATTERY_LEVEL)
        batteryCharacteristic?.let { requestBatteryLevel(it) }
    }

    open fun closeConnection() {
        mapping.clear()
        saveTime.clear()
        valuesSize = 0
        if (!ownsConnection)
            return //not ours to close - the owning block does that
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

    @Throws(BluetoothException::class)
    open fun start() {
        if (!isConnected()) {
            throw BluetoothException(context.resources.getString(R.string.bt_exception_no_connection), this)
        }
        forcedBreak = false
        isRunning = true
        startAcquisition()
    }

    open fun stop() {
        isRunning = false
        stopAcquisition()
        mainHandler.post { toast?.cancel() }
        //queue and reconnect belong to the shared connection, dropped only once no block is running
        if (sharing.none { it.isRunning }) {
            owner.reconnectJob?.cancel()
            owner.reconnectJob = null
            queue?.clear()
        }
    }

    /** Subclass hook, also called after a reconnect while the experiment keeps running */
    @Throws(BluetoothException::class)
    protected open fun startAcquisition() {
    }

    /** Subclass hook, also called when the connection breaks while the experiment keeps running */
    protected open fun stopAcquisition() {
    }

    /** Called on a notification */
    protected open fun retrieveData(data: ByteArray, characteristic: BluetoothGattCharacteristic) {
    }

    /** Called with the result of a queued read, data is null if it failed */
    protected open fun saveData(data: ByteArray?, characteristic: BluetoothGattCharacteristic) {
    }

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

    // Queue access for subclasses and CharacteristicData

    @Throws(BluetoothException::class)
    internal fun awaitWrite(characteristic: UUID, value: ByteArray) {
        val q = queue ?: throw BluetoothException(context.resources.getString(R.string.bt_exception_no_connection), this)
        val result = runBlocking { q.run(BleOp.Write(characteristic, value)) }
        if (!result.ok) {
            throw BluetoothException(context.resources.getString(R.string.bt_fail_writing), this)
        }
    }

    internal fun awaitWriteDescriptor(characteristic: UUID, descriptor: UUID, value: ByteArray): Boolean {
        val q = queue ?: return false
        return runBlocking { q.run(BleOp.WriteDescriptor(characteristic, descriptor, value)) }.ok
    }

    internal fun submitDataWrite(characteristic: UUID, value: ByteArray) {
        queue?.enqueue(BleOp.Write(characteristic, value, coalescible = true))
    }

    internal fun submitControlWrite(characteristic: UUID, value: ByteArray) {
        queue?.enqueue(BleOp.Write(characteristic, value))
    }

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

    // Error handling

    //toast while the experiment is running, dialog otherwise
    protected fun displayErrorMessage(message: String?) {
        displayErrorMessage(message, !isRunning)
    }

    protected fun displayErrorMessage(message: String?, showDialog: Boolean) {
        if (showDialog) {
            errorDialog.message = message ?: ""
            mainHandler.post(errorDialog)
        } else {
            //throttled so a reconnect loop cannot spam the user
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

    //repeated timeouts without a disconnect callback: treat it like a disconnect
    private fun onLinkDead() {
        if (isRunning) {
            handleDisconnect()
        }
    }

    //Retries with exponential backoff while the experiment runs. Called from GATT binder threads and
    // the queue worker, so anything that may block on a queue operation runs on deviceScope.
    private fun handleDisconnect() {
        queue?.clear()
        synchronized(this) {
            if (sharing.none { it.isRunning } || reconnectJob?.isActive == true)
                return
            for (b in sharing)
                b.forcedBreak = true
            reconnectJob = deviceScope.launch {
                for (b in sharing)
                    b.pauseAcquisition()
                displayErrorMessage(context.resources.getString(R.string.bt_exception_disconnected) + BluetoothException.getMessage(this@Bluetooth), false)

                var backoff = RECONNECT_INITIAL_BACKOFF_MS
                while (isActive && sharing.any { it.isRunning }) {
                    delay(backoff)
                    try {
                        //the owner reopens the connection, then every block looks its characteristics up again
                        for (b in sharing)
                            b.connect(null)
                        for (b in sharing) {
                            if (b.isRunning) {
                                b.forcedBreak = false
                                b.resumeAcquisition()
                            }
                        }
                        mainHandler.post { toast?.cancel() }
                        return@launch
                    } catch (e: BluetoothException) {
                        displayErrorMessage(e.message, false)
                        backoff = min(backoff * 2, RECONNECT_MAX_BACKOFF_MS)
                    }
                }
            }
        }
    }

    //same-class access to the protected hooks, so the owner can drive the blocks sharing its connection
    internal fun dispatchNotification(data: ByteArray, characteristic: BluetoothGattCharacteristic) =
        retrieveData(data, characteristic)

    internal fun pauseAcquisition() = stopAcquisition()

    @Throws(BluetoothException::class)
    internal fun resumeAcquisition() = startAcquisition()

    // GATT plumbing

    @Transient
    private val gattIo = BleGattIo { btGatt }

    @Transient
    private val btLeGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            lastConnectionStatus = status
            //ignore callbacks of a replaced GATT client; btGatt is still null while a fresh attempt is in
            //flight (the callback can fire before connectGatt has returned), so that case has to pass
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
            //an input and an output block may share this connection; one that does not map the characteristic ignores it
            for (b in sharing)
                b.dispatchNotification(data, characteristic)
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
        mainHandler.post {
            Experiment.updateConnectedDeviceDelegate?.updateConnectedDevice(arrayListOf(connectedDeviceInformation))
        }
    }

    //Writes status and time references to the phyphox event characteristic on start/pause/clear/connect
    fun writeEventCharacteristic(timeMapping: ExperimentTimeReference.TimeMapping?) {
        val eventChar = eventCharacteristic
        if (forcedBreak || eventChar == null)
            return
        val out = ByteArray(17)

        //byte 0: 0x00 pause, 0x01 start, 0x02 clear, 0xff connection established
        out[0] = when (timeMapping?.event) {
            ExperimentTimeReference.TimeMappingEvent.PAUSE -> 0x00
            ExperimentTimeReference.TimeMappingEvent.START -> 0x01
            ExperimentTimeReference.TimeMappingEvent.CLEAR -> 0x02
            null -> 0xff.toByte()
        }

        //bytes 1-8: experiment time in ms as int64 big endian (like the existing characteristics), -1 if no measurement ran yet
        val experimentTimeMillis = if (timeMapping != null) (timeMapping.experimentTime * 1000).toLong() else -1L
        for (i in 0 until 8)
            out[1 + i] = (experimentTimeMillis shr (56 - 8 * i)).toByte()

        //bytes 9-16: system time in ms since 1970, same format
        val systemTimeMillis = timeMapping?.systemTime ?: System.currentTimeMillis()
        for (i in 0 until 8)
            out[9 + i] = (systemTimeMillis shr (56 - 8 * i)).toByte()

        submitControlWrite(eventChar.uuid, out)
    }

    /** Attributes of a characteristic as defined in the phyphox file */
    class Characteristic {
        @JvmField
        val index: Int //buffer index

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

    /** Data of a characteristic collected from the phyphox file */
    abstract class CharacteristicData(@JvmField val uuid: UUID) : Serializable {
        /** Called once connected: registers the characteristic or writes its configuration */
        @Throws(BluetoothException::class)
        abstract fun process(b: Bluetooth)
    }

    /** A characteristic recorded by a BluetoothInput */
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

    /** A characteristic written with buffer data by a BluetoothOutput */
    class OutputData(uuid: UUID, @JvmField val index: Int, @JvmField val conversionFunction: ConversionsOutput.OutputConversion?, @JvmField val offset: Short, @JvmField val triggerId: String?) : CharacteristicData(uuid), Serializable {

        @Throws(BluetoothException::class)
        override fun process(b: Bluetooth) {
            val c = b.findCharacteristic(uuid)
            b.mapping.getOrPut(c) { ArrayList() }.add(Characteristic(index, conversionFunction, offset, triggerId))
            b.valuesSize++
        }
    }

    /** A characteristic that receives a fixed configuration value on connect */
    class ConfigData : CharacteristicData, Serializable {
        @JvmField
        val value: ByteArray

        @Throws(PhyphoxFile.phyphoxFileException::class)
        constructor(uuid: UUID, data: String, conversionFunction: ConversionsConfig.ConfigConversion) : super(uuid) {
            try {
                this.value = conversionFunction.convert(data)
            } catch (e: Exception) {
                throw PhyphoxFile.phyphoxFileException("An error occurred on the conversion function \"" + conversionFunction.javaClass.name + "\". ")
            }
        }

        @Throws(BluetoothException::class)
        override fun process(b: Bluetooth) {
            val c = b.findCharacteristic(uuid)
            b.awaitWrite(c.uuid, value)
        }
    }

    class BluetoothException(message: String, b: Bluetooth) : Exception(message + getMessage(b)) {
        companion object {
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

    /** Shows an error dialog with a try-again option */
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

    /** Connects all devices of an experiment on a background thread, with progress dialog and try-again error dialog */
    class ConnectBluetoothTask {
        @JvmField
        var progress: ProgressDialog? = null

        @JvmField
        var onSuccess: Runnable? = null

        fun execute(vararg params: Vector<out Bluetooth>) {
            Thread {
                var errorMessage: String? = null
                shareConnections(*params)
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

        //phyphox BLE GATT UUIDs: a contract shared with iOS and the Arduino/MicroPython libraries
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

        private const val TAG = "phyphoxBle"

        //One log line per finished connect/transfer, emitted at the end because the lab driver
        // (phyphox-docs tools/lab/ble.py, parse_retry_lines) clears logcat at scenario start
        internal fun reportBleOutcome(tag: String, event: String, attempts: Int, ok: Boolean,
                                      reason: String? = null, bytes: Int? = null,
                                      ms: Long? = null) {
            val line = StringBuilder(RETRY_TOKEN)
            line.append(" event=").append(event)
            //successful attempt included, so 1 means it worked first time
            line.append(" attempts=").append(attempts)
            line.append(" result=").append(if (ok) "ok" else "failed")
            //parsed as space separated key=value, so no value may contain a space
            reason?.let { line.append(" reason=").append(it.replace(' ', '_')) }
            bytes?.let { line.append(" bytes=").append(it) }
            ms?.let { line.append(" ms=").append(it) }
            Log.i(tag, line.toString())
        }

        private const val RETRY_TOKEN = "phyphox-ble-retries" //the lab greps for this literal

        const val CONNECT_TIMEOUT_MS = 10000L

        //Direct connect often fails with GATT_ERROR (133) and succeeds a moment later, and a board may still
        // be releasing the previous connection; a refused attempt (~0.35 s) is retried. The attempt count is
        // only a ceiling, a switched-off device burns CONNECT_TIMEOUT_MS per attempt, so the clock bounds it.
        const val CONNECT_ATTEMPTS = 6
        const val CONNECT_RETRY_DELAY_MS = 500L
        const val CONNECT_TOTAL_BUDGET_MS = 25000L
        const val RSSI_INTERVAL_MS = 1000L
        const val TOAST_THROTTLE_MS = 5000L
        const val RECONNECT_INITIAL_BACKOFF_MS = 1000L
        const val RECONNECT_MAX_BACKOFF_MS = 30000L

        @JvmField
        var errorDialog = OnExceptionRunnable()

        internal var btAdapter: BluetoothAdapter? = null

        private val bleThread by lazy {
            HandlerThread("phyphoxBLE").also { it.start() }
        }

        internal val bleScope by lazy {
            CoroutineScope(SupervisorJob() + Handler(bleThread.looper).asCoroutineDispatcher("phyphoxBLE"))
        }

        @JvmStatic
        fun isSupported(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        }

        @JvmStatic
        fun isEnabled(): Boolean {
            if (btAdapter == null) {
                btAdapter = BluetoothAdapter.getDefaultAdapter()
            }
            return btAdapter?.isEnabled == true
        }

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

        //Blocks with the same id describe one physical device and share one GATT connection, owned by the
        // first in connection order; a block without an id is its own device. Called before every
        // connection attempt, it also resets the grouping.
        @JvmStatic
        fun shareConnections(vararg list: Vector<out Bluetooth>) {
            val owners = HashMap<String, Bluetooth>()
            for (v in list) {
                for (b in v) {
                    b.owner = b
                    b.sharing = mutableListOf(b)
                }
            }
            for (v in list) {
                for (b in v) {
                    val id = b.idString
                    if (id.isNullOrEmpty())
                        continue
                    val existing = owners[id]
                    if (existing == null) {
                        owners[id] = b
                    } else {
                        b.owner = existing
                        b.sharing = existing.sharing
                        existing.sharing.add(b)
                    }
                }
            }
        }

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
