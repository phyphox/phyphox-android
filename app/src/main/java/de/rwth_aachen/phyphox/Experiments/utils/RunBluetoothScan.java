package de.rwth_aachen.phyphox.Experiments.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

import de.rwth_aachen.phyphox.Bluetooth.Bluetooth;
import de.rwth_aachen.phyphox.Bluetooth.BluetoothScanDialog;
import de.rwth_aachen.phyphox.Experiments.view.ExperimentsInCategory;
import de.rwth_aachen.phyphox.R;

//The BluetoothScanDialog has been written to block execution until a device is found, so we should not run it on the UI thread.
public class RunBluetoothScan  extends AsyncTask<String, Void, BluetoothScanDialog.BluetoothDeviceInfo> {
    private final WeakReference<Activity> parent;

    private HashMap<String, Vector<String>> bluetoothDeviceNameList = new HashMap<>(); //This will collect names of Bluetooth devices and maps them to (hidden) experiments supporting these devices
    private HashMap<UUID, Vector<String>> bluetoothDeviceUUIDList = new HashMap<>(); //This will collect uuids of Bluetooth devices (services or characteristics) and maps them to (hidden) experiments supporting these devices


    //The constructor takes the intent to copy from and the parent activity to call back when finished.
    public RunBluetoothScan(Activity parent) {
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
                    CommonMethods commonMethods = new CommonMethods( parent.get(), parent.get());
                    commonMethods.scanQRCode();
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

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @SuppressLint("MissingPermission") //TODO: The permission is actually checked when entering the entire BLE dialog and I do not see how we could reach this part of the code if it failed. However, I cannot rule out some other mechanism of revoking permissions during an app switch or from the notifications bar (?), so a cleaner implementation might be good idea
    public void openBluetoothExperiments(final BluetoothDevice device, final Set<UUID> uuids, boolean phyphoxService) {

        Set<String> experiments = new HashSet<>();
        if (device.getName() != null) {
            for (String name : bluetoothDeviceNameList.keySet()) {
                if (device.getName().contains(name))
                    experiments.addAll(bluetoothDeviceNameList.get(name));
            }
        }

        for (UUID uuid : uuids) {
            Vector<String> experimentsForUUID = bluetoothDeviceUUIDList.get(uuid);
            if (experimentsForUUID != null)
                experiments.addAll(experimentsForUUID);
        }
        final Set<String> supportedExperiments = experiments;

        if (supportedExperiments.isEmpty() && phyphoxService) {
            //We do not have any experiments for this device, so there is no choice. Just load the experiment provided by the device.
            //TODO loadExperimentFromBluetoothDevice(device);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(parent.get());
        LayoutInflater inflater = (LayoutInflater) parent.get().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.open_multipe_dialog, null);
        builder.setView(view);
        final Activity parent = this.parent.get();
        if (!supportedExperiments.isEmpty()) {
            builder.setPositiveButton(R.string.open_save_all, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    AssetManager assetManager = parent.getAssets();
                    try {
                        for (String file : supportedExperiments) {
                            InputStream in = assetManager.open("experiments/bluetooth/" + file);
                            OutputStream out = new FileOutputStream(new File(parent.getFilesDir(), UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox"));
                            byte[] buffer = new byte[1024];
                            int count;
                            while ((count = in.read(buffer)) != -1) {
                                out.write(buffer, 0, count);
                            }
                            in.close();
                            out.flush();
                            out.close();
                        }
                    } catch (Exception e) {
                        Toast.makeText(parent, "Error: Could not retrieve assets.", Toast.LENGTH_LONG).show();
                    }

                    //TODO loadExperimentList();
                    dialog.dismiss();
                }

            });
        }
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }

        });

        String instructions = "";
        if (!supportedExperiments.isEmpty()) {
            instructions += parent.getString(R.string.open_bluetooth_assets);
        }
        if (!supportedExperiments.isEmpty() && phyphoxService)
            instructions += "\n\n";
        if (phyphoxService) {
            instructions += parent.getString(R.string.newExperimentBluetoothLoadFromDeviceInfo);
            builder.setNeutralButton(R.string.newExperimentBluetoothLoadFromDevice, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    //TODO loadExperimentFromBluetoothDevice(device);
                    dialog.dismiss();
                }
            });
        }
        AlertDialog dialog = builder.create();

        ((TextView)view.findViewById(R.id.open_multiple_dialog_instructions)).setText(instructions);

        LinearLayout catList = (LinearLayout)view.findViewById(R.id.open_multiple_dialog_list);

        dialog.setTitle(parent.getResources().getString(R.string.open_bluetooth_assets_title));

        //Load experiments from assets
        AssetManager assetManager = parent.getAssets();
        Vector<ExperimentsInCategory> bluetoothExperiments = new Vector<>();
        for (String file : supportedExperiments) {
            //Load details for each experiment
            try {
                InputStream input = assetManager.open("experiments/bluetooth/"+file);
               //TODO  loadExperimentInfo(input, "bluetooth/"+file, "bluetooth", true, bluetoothExperiments, null, null);
                input.close();
            } catch (IOException e) {
                Log.e("bluetooth", e.getMessage());
                Toast.makeText(parent, "Error: Could not load experiment \"" + file + "\" from asset.", Toast.LENGTH_LONG).show();
            }
        }

        Collections.sort(bluetoothExperiments, new CategoryComparator());

        for (ExperimentsInCategory cat : bluetoothExperiments) {
            cat.setPreselectedBluetoothAddress(device.getAddress());
            cat.addToParent(catList);
        }

        dialog.show();
    }



}
