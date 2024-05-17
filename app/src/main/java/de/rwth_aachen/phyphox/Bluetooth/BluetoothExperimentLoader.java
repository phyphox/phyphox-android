package de.rwth_aachen.phyphox.Bluetooth;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.CRC32;

import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.ExperimentList;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.R;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission") //TODO: The permission is actually checked when entering the entire BLE dialog and I do not see how we could reach this part of the code if it failed. However, I cannot rule out some other mechanism of revoking permissions during an app switch or from the notifications bar (?), so a cleaner implementation might be good idea

public class BluetoothExperimentLoader {
    byte[] currentBluetoothData;
    int currentBluetoothDataSize;
    int currentBluetoothDataIndex;
    long currentBluetoothDataCRC32;

    boolean active = false;

    public interface BluetoothExperimentLoaderCallback {
        void updateProgress(int transferred, int total);
        void dismiss();
        void error(String msg);
        void success(Uri ExperimentUri, boolean isZip);
    }

    BluetoothExperimentLoaderCallback callback;
    Context ctx;

    BluetoothGattDescriptor descriptor = null;
    BluetoothGatt gatt = null;
    BluetoothGattCharacteristic experimentCharacteristic = null;

    public BluetoothExperimentLoader(Context ctx, BluetoothExperimentLoaderCallback callback) {
        this.callback = callback;
        this.ctx = ctx;
    }

    public void loadExperimentFromBluetoothDevice(final BluetoothDevice device) {
        currentBluetoothData = null;
        currentBluetoothDataSize = 0;
        currentBluetoothDataIndex = 0;
        descriptor = null;
        active = false;

        gatt = device.connectGatt(ctx, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        // fall through to default
                    default:
                        callback.dismiss();
                        gatt.close();
                        return;
                }
            }

            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    callback.error(ctx.getString(R.string.bt_exception_notification) + " " + Bluetooth.phyphoxExperimentCharacteristicUUID.toString() + " " + ctx.getString(R.string.bt_exception_notification_enable) + " (could not discover services)");
                    return;
                }

                //Find characteristic
                BluetoothGattService phyphoxService = gatt.getService(Bluetooth.phyphoxServiceUUID);
                if (phyphoxService == null) {
                    gatt.disconnect();
                    callback.error(ctx.getString(R.string.bt_exception_notification) + " " + Bluetooth.phyphoxExperimentCharacteristicUUID.toString() + " " + ctx.getString(R.string.bt_exception_notification_enable) + " (no phyphox service)");
                    return;
                }
                experimentCharacteristic = phyphoxService.getCharacteristic(Bluetooth.phyphoxExperimentCharacteristicUUID);
                if (experimentCharacteristic == null) {
                    gatt.disconnect();
                    callback.error(ctx.getString(R.string.bt_exception_notification) + " " + Bluetooth.phyphoxExperimentCharacteristicUUID.toString() + " " + ctx.getString(R.string.bt_exception_notification_enable) + " (no experiment characteristic)");
                    return;
                }

                active = true;

                //Enable notifications
                if (!gatt.setCharacteristicNotification(experimentCharacteristic, true)) {
                    gatt.disconnect();
                    callback.error(ctx.getString(R.string.bt_exception_notification) + " " + Bluetooth.phyphoxExperimentCharacteristicUUID.toString() + " " + ctx.getString(R.string.bt_exception_notification_enable) + " (set char notification failed)");
                    return;
                }

                if ((experimentCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    descriptor = experimentCharacteristic.getDescriptor(BluetoothInput.CONFIG_DESCRIPTOR);
                    if (descriptor == null) {
                        gatt.disconnect();
                        callback.error(ctx.getString(R.string.bt_exception_notification) + " " + Bluetooth.phyphoxExperimentCharacteristicUUID.toString() + " " + ctx.getString(R.string.bt_exception_notification_enable) + " (descriptor failed)");
                        return;
                    }

                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                } else {
                    setControlCharacteristicIfAvailable(1);
                }
            }

            @Override
            public void onCharacteristicChanged(final BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                if (!characteristic.getUuid().equals(Bluetooth.phyphoxExperimentCharacteristicUUID))
                    return;
                byte[] data = characteristic.getValue();
                dataIn(data);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
                if (!characteristic.getUuid().equals(Bluetooth.phyphoxExperimentCharacteristicUUID))
                    return;
                dataIn(value);
                if (currentBluetoothDataIndex < currentBluetoothDataSize)
                    gatt.readCharacteristic(characteristic);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    disconnect();
                    callback.error(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) +  " (could not write)");
                }
                if (descriptor == null && experimentCharacteristic != null)
                    gatt.readCharacteristic(experimentCharacteristic);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    disconnect();
                    callback.error(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) +  " (could not write descriptor)");
                    return;
                }

                setControlCharacteristicIfAvailable(1);
            }
        });

    }

    private void disconnect() {
            //If phyphoxExperimentControlCharacteristicUUID is available, we can tell the device that we are no longer expecting the transfer by writing 0
            setControlCharacteristicIfAvailable(0);
            active = false;
            gatt.disconnect();
    }

    public void cancel() {
        active = false;
        if (gatt != null) {
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
                descriptor = null;
            }
            disconnect();
        }
        callback.dismiss();
    }

    private void dataIn(byte[] data) {
        if (!active)
            return;
        if (currentBluetoothData == null) {
            String header = new String(data);
            if (!header.startsWith("phyphox")) {
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
                disconnect();
                callback.error(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) +  " (invalid header)");
            }
            currentBluetoothDataSize = 0;
            currentBluetoothDataIndex = 0;
            for (int i = 0; i < 4; i++) {
                currentBluetoothDataSize <<= 8;
                currentBluetoothDataSize |= (data[7+i] & 0xFF);
            };
            currentBluetoothDataCRC32 = 0;
            for (int i = 0; i < 4; i++) {
                currentBluetoothDataCRC32 <<= 8;
                currentBluetoothDataCRC32 |= (data[7+4+i] & 0xFF);
            }

            if (currentBluetoothDataSize > 10e6 || currentBluetoothDataSize < 0) {
                disconnect();
                callback.error(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) +  " (invalid size in header)");
                callback.dismiss();
                currentBluetoothDataSize = 0;
            }

            currentBluetoothData = new byte[currentBluetoothDataSize];

            //From here we can estimate the progress, so let's show a determinate progress dialog instead
            callback.dismiss();
            callback.updateProgress(0, currentBluetoothDataSize);
        } else {
            int size = data.length;

            if (currentBluetoothDataIndex + size > currentBluetoothDataSize)
                size = currentBluetoothDataSize - currentBluetoothDataIndex;

            System.arraycopy(data, 0, currentBluetoothData, currentBluetoothDataIndex, size);
            currentBluetoothDataIndex += size;

            callback.updateProgress(currentBluetoothDataIndex, currentBluetoothDataSize);
            if (currentBluetoothDataIndex >= currentBluetoothDataSize) {
                //We are done. Check and use result

                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
                disconnect();

                if (currentBluetoothDataSize == 0) {
                    callback.dismiss();
                    return;
                }

                        /*
                        StringBuilder sb = new StringBuilder(currentBluetoothData.length * 3);
                        for(byte b: currentBluetoothData)
                            sb.append(String.format("%02x ", b));
                        final String hex = sb.toString();

                        for (int i = 0; i < hex.length(); i+= 48) {
                            Log.d("TEST", hex.substring(i, Math.min(i+48, hex.length())));
                        }
                        */

                CRC32 crc32 = new CRC32();
                crc32.update(currentBluetoothData);
                if (crc32.getValue() != currentBluetoothDataCRC32) {
                    callback.error(ctx.getString(R.string.newExperimentBTReadErrorCorrupted) +  " (CRC32)");
                    //   Log.d("TEST", "CRC32: Expected " + currentBluetoothDataCRC32 + " but calculated " + crc32.getValue());
                    return;
                }

                File tempPath = new File(ctx.getFilesDir(), "temp_bt");
                if (!tempPath.exists()) {
                    if (!tempPath.mkdirs()) {
                        callback.error("Could not create temporary directory to write bluetooth experiment file.");
                        return;
                    }
                }
                String[] files = tempPath.list();
                for (String file : files) {
                    if (!(new File(tempPath, file).delete())) {
                        callback.error("Could not clear temporary directory to extract bluetooth experiment file.");
                        return;
                    }
                }

                if (currentBluetoothData[0] == '<'
                        && currentBluetoothData[1] == 'p'
                        && currentBluetoothData[2] == 'h'
                        && currentBluetoothData[3] == 'y'
                        && currentBluetoothData[4] == 'p'
                        && currentBluetoothData[5] == 'h'
                        && currentBluetoothData[6] == 'o'
                        && currentBluetoothData[7] == 'x') {
                    //This is just an XML file, store it
                    File xmlFile;
                    try {
                        xmlFile = new File(tempPath, "bt.phyphox");
                        FileOutputStream out = new FileOutputStream(xmlFile);
                        out.write(currentBluetoothData);
                        out.close();
                    } catch (Exception e) {
                        callback.error("Could not write Bluetooth experiment content to phyphox file.");
                        return;
                    }

                    callback.success(Uri.fromFile(xmlFile), false);
                } else {

                    byte[] finalBluetoothData = Helper.inflatePartialZip(currentBluetoothData);

                    File zipFile;
                    try {
                        zipFile = new File(tempPath, "bt.zip");
                        FileOutputStream out = new FileOutputStream(zipFile);
                        out.write(finalBluetoothData);
                        out.close();
                    } catch (Exception e) {
                        callback.error("Could not write Bluetooth experiment content to zip file.");
                        return;
                    }

                    callback.success(Uri.fromFile(zipFile), true);
                }
            }
        }
    }

    private void setControlCharacteristicIfAvailable(int v) {
        //If phyphoxExperimentControlCharacteristicUUID is also present, the device expects us to initiate transfer by setting it to 1.
        BluetoothGattService phyphoxService = gatt.getService(Bluetooth.phyphoxServiceUUID);
        if (phyphoxService != null) {
            BluetoothGattCharacteristic experimentControlCharacteristic = phyphoxService.getCharacteristic(Bluetooth.phyphoxExperimentControlCharacteristicUUID);
            if (experimentControlCharacteristic != null) {
                experimentControlCharacteristic.setValue(v, FORMAT_UINT8, 0);
                gatt.writeCharacteristic(experimentControlCharacteristic);
            }
        }
    }
}
