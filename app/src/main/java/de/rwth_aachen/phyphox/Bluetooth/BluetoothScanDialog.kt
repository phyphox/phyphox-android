package de.rwth_aachen.phyphox.Bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.rwth_aachen.phyphox.R
import de.rwth_aachen.phyphox.helper.Helper
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scans for BLE devices and lets the user pick one. In autoConnect mode only a progress message
 * is shown and the first matching device is picked automatically.
 *
 * [getBluetoothDevice] is designed to block until a device has been picked or the dialog has
 * been dismissed, so it must be called from a background thread (both the connection process of
 * the Bluetooth engine and the scan from the experiment list run on one).
 *
 * A device matches by advertised name and/or advertised service UUIDs. The "supported" filters
 * do not hide devices, they mark which list entries are selectable (devices for which the app
 * has a matching experiment or which advertise the phyphox service for an experiment download).
 */
@SuppressLint("MissingPermission") //scanPermission() is checked before any scan is started
class BluetoothScanDialog(
    private val autoConnect: Boolean,
    private val parentActivity: Activity,
    private val ctx: Context,
    private val bta: BluetoothAdapter
) {

    class BluetoothDeviceInfo(
        @JvmField val device: BluetoothDevice,
        /** resolved once when the device is first seen - BluetoothDevice.getName() is a binder IPC call */
        val name: String,
        @JvmField var supported: Boolean,
        @JvmField var phyphoxService: Boolean,
        @JvmField val uuids: MutableSet<UUID>,
        @JvmField var lastRSSI: Int
    ) {
        /** more than one device with this name is in range (highlight the strongest one) */
        var oneOfMany = false
        var strongestSignal = true

        val selectable get() = supported || phyphoxService
    }

    private var dialog: AlertDialog? = null
    private var title: TextView? = null
    private var listAdapter: DeviceListAdapter? = null
    private val lock = Object()

    /** all matching devices seen so far, keyed by MAC address, guarded by itself */
    private val foundDevices = LinkedHashMap<String, BluetoothDeviceInfo>()

    private val uiHandler = Handler(Looper.getMainLooper())
    private val listUpdatePending = AtomicBoolean(false)

    @Volatile
    private var selectedDevice: BluetoothDeviceInfo? = null

    private var nameFilter: String? = null
    private var uuidFilter: UUID? = null
    private var supportedNameFilter: Set<String>? = null
    private var supportedUUIDFilter: Set<UUID>? = null

    init {
        parentActivity.runOnUiThread {
            val builder = AlertDialog.Builder(parentActivity)

            if (!autoConnect) {
                val view = (ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.bluetooth_scan_dialog, null)
                builder.setView(view)
                    .setPositiveButton(R.string.bt_more_info_link_button) { d, _ ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(R.string.bt_more_info_link_url)))
                        if (intent.resolveActivity(parentActivity.packageManager) != null)
                            parentActivity.startActivity(intent)
                        d.dismiss()
                    }
                title = view.findViewById(R.id.bluetooth_scan_dialog_title)
                val list = view.findViewById<ListView>(R.id.bluetooth_scan_dialog_items)
                val adapter = DeviceListAdapter()
                listAdapter = adapter
                list.adapter = adapter
                list.setOnItemClickListener { _, _, pos, _ ->
                    val device = adapter.getDevice(pos)
                    if (device.selectable) {
                        selectedDevice = device
                        dialog?.dismiss()
                    }
                }
            }
            builder.setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }

            dialog = builder.create().also { d ->
                d.setOnDismissListener {
                    synchronized(lock) {
                        lock.notify()
                    }
                }
                if (!autoConnect)
                    d.setTitle(ctx.resources.getString(R.string.bt_pick_device))
            }
        }
    }

    /**
     * Scan and block until the user picks a device (or, in autoConnect mode, the first match is
     * found) or the dialog is cancelled. Returns null if cancelled or if scanning is not
     * possible (missing permission, disabled location service, Bluetooth turned off).
     */
    fun getBluetoothDevice(
        nameFilter: String?,
        uuidFilter: UUID?,
        supportedNameFilter: Set<String>?,
        supportedUUIDFilter: Set<UUID>?,
        idString: String?
    ): BluetoothDeviceInfo? {
        this.nameFilter = nameFilter
        this.uuidFilter = uuidFilter
        this.supportedNameFilter = supportedNameFilter
        this.supportedUUIDFilter = supportedUUIDFilter

        if (!scanPermission())
            return null
        if (!locationEnabled())
            return null
        val scanner = bta.bluetoothLeScanner ?: return null //Bluetooth is turned off

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)

        parentActivity.runOnUiThread {
            val idSuffix = if (idString.isNullOrEmpty()) "" else " ($idString)"
            val notification = if (nameFilter.isNullOrEmpty())
                ctx.resources.getString(R.string.bt_scanning_generic) + idSuffix
            else
                ctx.resources.getString(R.string.bt_scanning_specific1) + " \"" + nameFilter + "\" " + ctx.resources.getString(R.string.bt_scanning_specific2) + idSuffix
            if (autoConnect)
                dialog?.setMessage(notification)
            else
                title?.text = notification
            dialog?.show()
        }

        synchronized(lock) {
            try {
                lock.wait()
            } catch (e: InterruptedException) {
                //return what we have
            }
        }

        try {
            scanner.stopScan(scanCallback)
        } catch (e: Exception) {
            //Bluetooth might have been turned off while the dialog was open
        }

        return selectedDevice
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results)
                handleScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BluetoothScanDialog", "BLE scan failed with error code $errorCode.")
            parentActivity.runOnUiThread { dialog?.dismiss() }
        }
    }

    //Called for every received advertisement, i.e. at a high rate when many devices are in
    // range. Bookkeeping is done here on the callback thread with cheap operations only (no
    // binder calls for known devices, no UI work) and the visible list is rebuilt at a limited
    // rate by scheduleListUpdate().
    private fun handleScanResult(result: ScanResult) {
        val address = result.device.address
        val advertisedUuids = result.scanRecord?.serviceUuids

        val deviceInfo: BluetoothDeviceInfo
        synchronized(foundDevices) {
            val existing = foundDevices[address]
            if (existing != null) {
                existing.lastRSSI = result.rssi
                advertisedUuids?.forEach { existing.uuids.add(it.uuid) }
                if (!existing.phyphoxService && Bluetooth.phyphoxServiceUUID in existing.uuids)
                    existing.phyphoxService = true
                if (!existing.supported && supportedUUIDFilter?.any { it in existing.uuids } == true)
                    existing.supported = true
                deviceInfo = existing
            } else {
                //First sighting of this device: resolve the name and apply the filters. The
                // name cached by the stack takes precedence, as it may hold the full name from
                // the GAP device name characteristic (0x2a00) of a previous connection, while
                // the advertisement may only carry a shortened form - and some devices append
                // distinguishing codes to the full name that students rely on to tell devices
                // apart in class. The advertised name is only the fallback for devices the
                // stack has no name for; it is used as is by some devices and may contain
                // surrounding whitespace or even a line break.
                val name = (result.device.name ?: result.scanRecord?.deviceName)?.trim() ?: return
                if (name.isEmpty())
                    return
                val filter = nameFilter
                if (!filter.isNullOrEmpty() && !name.contains(filter))
                    return

                val uuids: MutableSet<UUID> = advertisedUuids?.mapTo(HashSet()) { it.uuid } ?: HashSet()
                uuidFilter?.let { required ->
                    if (required !in uuids)
                        return
                }

                var supported = supportedNameFilter.isNullOrEmpty() || supportedNameFilter!!.any { name.contains(it) }
                if (!supported && supportedUUIDFilter?.any { it in uuids } == true)
                    supported = true

                val phyphoxService = Bluetooth.phyphoxServiceUUID in uuids
                deviceInfo = BluetoothDeviceInfo(result.device, name, supported, phyphoxService, uuids, result.rssi)
                foundDevices[address] = deviceInfo
            }
        }

        if (autoConnect) {
            if (deviceInfo.selectable) {
                selectedDevice = deviceInfo
                parentActivity.runOnUiThread { dialog?.dismiss() }
            }
        } else {
            scheduleListUpdate()
        }
    }

    /**
     * Rebuild the visible device list at most every LIST_UPDATE_INTERVAL_MS, no matter how fast
     * scan results come in.
     */
    private fun scheduleListUpdate() {
        if (!listUpdatePending.compareAndSet(false, true)) {
            return
        }
        uiHandler.postDelayed({
            listUpdatePending.set(false)

            val devices = synchronized(foundDevices) { ArrayList(foundDevices.values) }

            //Mark devices that share their name with another one and among those the one with
            // the strongest signal, which is most likely the one the user is looking for.
            for (device in devices) {
                device.oneOfMany = false
                device.strongestSignal = true
            }
            for (i in devices.indices) {
                for (j in i + 1 until devices.size) {
                    val a = devices[i]
                    val b = devices[j]
                    if (a.name == b.name) {
                        a.oneOfMany = true
                        b.oneOfMany = true
                        if (a.lastRSSI >= b.lastRSSI)
                            b.strongestSignal = false
                        else
                            a.strongestSignal = false
                    }
                }
            }

            listAdapter?.update(devices)
        }, LIST_UPDATE_INTERVAL_MS)
    }

    fun scanPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Android 6-11: BLE scanning requires the location permission
            parentActivity.runOnUiThread {
                AlertDialog.Builder(parentActivity)
                    .setMessage(parentActivity.resources.getText(R.string.bt_location_explanation))
                    .setCancelable(true)
                    .setPositiveButton(parentActivity.resources.getText(R.string.doContinue)) { d, _ ->
                        d.cancel()
                        ActivityCompat.requestPermissions(parentActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
                        //We will stop here. If the user grants the permission, the permission callback will restart the action
                    }
                    .setNegativeButton(parentActivity.resources.getText(R.string.cancel)) { d, _ -> d.cancel() }
                    .create()
                    .show()
            }
            return false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(parentActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            //Android 12+: dedicated Bluetooth permissions instead of the location permission
            parentActivity.runOnUiThread {
                dialog?.cancel()
                ActivityCompat.requestPermissions(parentActivity, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_SCAN_REQUEST_CODE)
            }
            return false
        }

        return true
    }

    fun locationEnabled(): Boolean {
        //Below Android 6 scanning does not require location access. From Android 12 on the
        // BLUETOOTH_SCAN permission is declared with neverForLocation, so the location service
        // is not required either.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            return true

        val locationManager = parentActivity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            //Android 6-11: Location service not enabled? Ask to enable it.
            parentActivity.runOnUiThread {
                AlertDialog.Builder(parentActivity)
                    .setMessage(parentActivity.resources.getText(R.string.bt_location_service_explanation))
                    .setCancelable(true)
                    .setPositiveButton(parentActivity.resources.getText(R.string.doContinue)) { d, _ ->
                        d.cancel()
                        parentActivity.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    .setNegativeButton(parentActivity.resources.getText(R.string.cancel)) { d, _ -> d.cancel() }
                    .create()
                    .show()
            }
            return false
        }
        return true
    }

    /** List of the found devices. Only accessed on the UI thread. */
    private inner class DeviceListAdapter : BaseAdapter() {
        private val devices = ArrayList<BluetoothDeviceInfo>()
        private val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        fun update(newDevices: List<BluetoothDeviceInfo>) {
            devices.clear()
            devices.addAll(newDevices)
            notifyDataSetChanged()
        }

        fun getDevice(position: Int): BluetoothDeviceInfo = devices[position]

        override fun getCount() = devices.size

        override fun getItem(i: Int): Any = devices[i]

        override fun getItemId(i: Int) = i.toLong()

        override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
            val entry = view ?: inflater.inflate(R.layout.bluetooth_scan_dialog_entry, null)
            val deviceName = entry.findViewById<TextView>(R.id.device_name)
            val notSupported = entry.findViewById<TextView>(R.id.device_not_supported)
            val signalStrength = entry.findViewById<ImageView>(R.id.signal_strength)

            val deviceInfo = devices[i]
            if (deviceInfo.name.isNotEmpty())
                deviceName.text = deviceInfo.name
            else
                deviceName.setText(R.string.unknown)

            if (deviceInfo.selectable) {
                notSupported.visibility = View.GONE
                (deviceName.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.CENTER_VERTICAL)
            } else {
                notSupported.visibility = View.VISIBLE
            }

            deviceName.setTextColor(
                if (deviceInfo.selectable) {
                    if (deviceInfo.oneOfMany && deviceInfo.strongestSignal)
                        ctx.resources.getColor(R.color.phyphox_primary)
                    else if (Helper.isDarkTheme(ctx.resources))
                        ctx.resources.getColor(R.color.phyphox_white_100)
                    else
                        ctx.resources.getColor(R.color.phyphox_black_80)
                } else {
                    ctx.resources.getColor(R.color.phyphox_white_50_black_50)
                }
            )

            signalStrength.setImageDrawable(ContextCompat.getDrawable(ctx, ConnectedDeviceInfo.getSignalStrengthDrawable(deviceInfo.lastRSSI)))
            signalStrength.setColorFilter(Helper.getAdjustedColorForImage(ctx))
            return entry
        }
    }

    companion object {
        const val BLUETOOTH_SCAN_REQUEST_CODE = 3

        /** minimum time between two updates of the visible device list */
        const val LIST_UPDATE_INTERVAL_MS = 300L
    }
}
