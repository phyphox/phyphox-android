package de.rwth_aachen.phyphox;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.ArrayList;
import java.util.Vector;


//The BluetoothOutput class encapsulates a generic serial output to bluetooth devices
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothOutput extends Bluetooth {
    public Vector<dataInput> data = new Vector<>(); //Data-buffers

    protected BluetoothOutput(String deviceName, String deviceAddress, Context context, Vector<dataInput> buffers, Vector<CharacteristicData> characteristics) {

        super(deviceName, deviceAddress, context, characteristics);

        this.data = buffers;

    }

    @Override
    public void stop () {
        super.stop();
        clear();
    }

    //This is called when new data should be written to the device
    public void sendData () {
        if (!isAvailable()) {
            try {
                connect();
            } catch (BluetoothException e) {
                handleException.setMessage(e.getMessage());
                mainHandler.post(handleException);
            }
        }
        for (BluetoothGattCharacteristic characteristic : mapping.keySet()) {
            for (Characteristic c : mapping.get(characteristic)) {
                if (data.get(c.index).getFilledSize() != 0) {
                    characteristic.setValue(data.get(c.index).getByteArray());
                    add(new WriteCommand(btGatt, characteristic));
                }
            }
        }
    }

}
