package de.rwth_aachen.phyphox;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.lang.reflect.Method;
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

    private byte[] convertData (double value, Method conversionFunction) {
        try {
            return (byte[]) conversionFunction.invoke(null, value);
        } catch (Exception e) {
            if (handleException != null) {
                handleException.setMessage(context.getResources().getString(R.string.bt_exception_conversion3)+" \"" + conversionFunction + "\". " + getDeviceData());
                mainHandler.post(handleException);
            }
        }
        return new byte[0]; // the method needs to return a byte array
    }

    //This is called when new data should be written to the device
    public void sendData () {
        if (!isAvailable()) {
            try {
                connect();
            } catch (BluetoothException e) {
                if (handleException != null) {
                    handleException.setMessage(e.getMessage());
                    mainHandler.post(handleException);
                }
            }
        }
        for (BluetoothGattCharacteristic characteristic : mapping.keySet()) {
            for (Characteristic c : mapping.get(characteristic)) {
                if (data.get(c.index).getFilledSize() != 0) {
                    byte[] value = convertData(data.get(c.index).getValue(), c.conversionFunction);
                    characteristic.setValue(value);
                    add(new WriteCommand(btGatt, characteristic));
                }
            }
        }
    }

}
