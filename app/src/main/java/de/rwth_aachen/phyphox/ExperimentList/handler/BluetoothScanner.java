package de.rwth_aachen.phyphox.ExperimentList.handler;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;

import java.util.Set;
import java.util.UUID;

import de.rwth_aachen.phyphox.Bluetooth.Bluetooth;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothScanDialog;
import de.rwth_aachen.phyphox.R;

//The BluetoothScanDialog has been written to block execution until a device is found, so we should not run it on the UI thread.
public class BluetoothScanner extends AsyncTask<String, Void, BluetoothScanDialog.BluetoothDeviceInfo> {

    public interface BluetoothScanListener {
        void onBluetoothDeviceFound(BluetoothScanDialog.BluetoothDeviceInfo result);

        void onBluetoothScanError(String msg, Boolean isError, Boolean isFatal);
    }

    private final BluetoothScanner.BluetoothScanListener listener;
    private final Resources res;

    private Activity parent;

    Set<String> bluetoothDeviceNameList;
    Set<UUID> bluetoothDeviceUUIDList;

    public BluetoothScanner(Activity parent, Set<String> bluetoothDeviceNameList, Set<UUID> bluetoothDeviceUUIDList, BluetoothScanner.BluetoothScanListener listener) {
        this.listener = listener;
        this.parent = parent;
        this.res = parent.getResources();
        this.bluetoothDeviceNameList = bluetoothDeviceNameList;
        this.bluetoothDeviceUUIDList = bluetoothDeviceUUIDList;

    }

    //Copying is done on a second thread...
    protected BluetoothScanDialog.BluetoothDeviceInfo doInBackground(String... params) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || !Bluetooth.isSupported(parent)) {
            listener.onBluetoothScanError(res.getString(R.string.bt_android_version), true, true);
            return null;
        } else {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null || !Bluetooth.isEnabled()) {
                listener.onBluetoothScanError(res.getString(R.string.bt_exception_disabled), true, false);
                return null;
            }
            BluetoothScanDialog bsd = new BluetoothScanDialog(false, parent, parent, btAdapter);

            return bsd.getBluetoothDevice(null, null, bluetoothDeviceNameList, bluetoothDeviceUUIDList, null);
        }
    }

    @Override
    protected void onPostExecute(BluetoothScanDialog.BluetoothDeviceInfo result) {
        if (result != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            listener.onBluetoothDeviceFound(result);
    }
}
