package de.rwth_aachen.phyphox.Experiments;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build;

import androidx.appcompat.app.AlertDialog;

import java.lang.ref.WeakReference;

import de.rwth_aachen.phyphox.Bluetooth.Bluetooth;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothScanDialog;
import de.rwth_aachen.phyphox.R;

//The BluetoothScanDialog has been written to block execution until a device is found, so we should not run it on the UI thread.
class RunBluetoothScan extends AsyncTask<String, Void, BluetoothScanDialog.BluetoothDeviceInfo> {
    private final WeakReference<Activity> parent;

    //The constructor takes the intent to copy from and the parent activity to call back when finished.
    RunBluetoothScan(Activity parent) {
        this.parent = new WeakReference<>(parent);
    }

    //Copying is done on a second thread...
    protected BluetoothScanDialog.BluetoothDeviceInfo doInBackground(String... params) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || !Bluetooth.isSupported(parent.get())) {
            showBluetoothScanError(parent.get().getResources().getString(R.string.bt_android_version), true, true);
            return null;
        } else {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter == null || !Bluetooth.isEnabled()) {
                showBluetoothScanError(parent.get().getResources().getString(R.string.bt_exception_disabled), true, false);
                return null;
            }
            BluetoothScanDialog bsd = new BluetoothScanDialog(false, parent.get(), parent.get(), btAdapter);
            return bsd.getBluetoothDevice(null, null, bluetoothDeviceNameList.keySet(), bluetoothDeviceUUIDList.keySet(), null);
        }
    }

    @Override
    //Call the parent callback when we are done.
    protected void onPostExecute(BluetoothScanDialog.BluetoothDeviceInfo result) {
        if (result != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            openBluetoothExperiments(result.device, result.uuids, result.phyphoxService);
    }

    protected void showBluetoothScanError(String msg, Boolean isError, Boolean isFatal) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(parent.get());
        builder.setMessage(msg)
                .setTitle(isError ? R.string.newExperimentBluetoothErrorTitle : R.string.newExperimentBluetooth);
        if (!isFatal) {
            builder.setPositiveButton(isError ? R.string.tryagain : R.string.doContinue, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    scanQRCode();
                }
            });
        }
        builder.setNegativeButton(parent.get().getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        });
        parent.get().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }
}
