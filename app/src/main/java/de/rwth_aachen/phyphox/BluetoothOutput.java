package de.rwth_aachen.phyphox;

import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import java.util.Vector;


//The BluetoothOutput class encapsulates a generic serial output to bluetooth devices
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothOutput extends Bluetooth {
    public Vector<dataInput> data = new Vector<>(); //Data-buffers

    protected BluetoothOutput(String deviceName, String deviceAddress, Context context, Vector<dataInput> buffers, Vector<CharacteristicData> characteristics) throws BluetoothException {

        super(deviceName, deviceAddress, context, characteristics);

        this.data = buffers;

    }

    public void stop () {
        clear();
    }

    //This is called when new data should be written to the device
    public void sendData () {
        if (!isAvailable()) {
            try {
                reconnect();
            } catch (BluetoothException e) {
                handleException.setMessage("A Bluetooth device is not connected. "+getDeviceData());
                mainHandler.post(handleException);
            }
        }
        for (Characteristic c : mapping.values()) {
            if (data.get(c.index).getFilledSize() != 0) {
                c.characteristic.setValue(data.get(c.index).getByteArray());
                add(new WriteCommand(btGatt, c.characteristic));
            }
        }
    }
}
