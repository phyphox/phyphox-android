package de.rwth_aachen.phyphox.ExperimentList.handler

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.os.Handler
import android.os.Looper
import de.rwth_aachen.phyphox.Bluetooth.Bluetooth
import de.rwth_aachen.phyphox.Bluetooth.BluetoothScanDialog
import de.rwth_aachen.phyphox.R
import java.util.UUID

/**
 * Scans for BLE devices from the experiment list, i.e. without an experiment definition: any
 * device that either matches an experiment bundled with the app (by advertised name or service
 * UUID) or that advertises the phyphox service (offering an experiment for download) can be
 * picked by the user.
 *
 * The scan itself is done by the [BluetoothScanDialog], which is designed to block until the
 * user picks a device, so it is run on a background thread and the listener is called on the
 * main thread.
 */
class BluetoothScanner(
    private val parent: Activity,
    private val bluetoothDeviceNameList: Set<String>,
    private val bluetoothDeviceUUIDList: Set<UUID>,
    private val listener: BluetoothScanListener
) {

    interface BluetoothScanListener {
        fun onBluetoothDeviceFound(result: BluetoothScanDialog.BluetoothDeviceInfo)
        fun onBluetoothScanError(msg: String, isError: Boolean, isFatal: Boolean)
    }

    fun execute() {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            val res = parent.resources
            if (!Bluetooth.isSupported(parent)) {
                mainHandler.post { listener.onBluetoothScanError(res.getString(R.string.bt_android_version), true, true) }
            } else if (BluetoothAdapter.getDefaultAdapter() == null || !Bluetooth.isEnabled()) {
                mainHandler.post { listener.onBluetoothScanError(res.getString(R.string.bt_exception_disabled), true, false) }
            } else {
                val scanDialog = BluetoothScanDialog(false, parent, parent, BluetoothAdapter.getDefaultAdapter())
                val result = scanDialog.getBluetoothDevice(null, null, bluetoothDeviceNameList, bluetoothDeviceUUIDList, null)
                val failure = scanDialog.scanFailureCode
                if (result != null)
                    mainHandler.post { listener.onBluetoothDeviceFound(result) }
                else if (failure != null) {
                    //A scan that never started used to end here in silence: the dialog closed
                    //itself and the user was left on the screen they came from, with the reason
                    //only in the log.
                    val msg = res.getString(R.string.bt_scan_failed) + " " + failure
                    mainHandler.post { listener.onBluetoothScanError(msg, true, false) }
                }
            }
        }.start()
    }
}
