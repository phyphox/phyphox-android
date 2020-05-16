package de.rwth_aachen.phyphox.Bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.UUID;
import java.util.Vector;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.DataInput;


/**
 * The BluetoothOutput class encapsulates an output to Bluetooth devices.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothOutput extends Bluetooth {

    /**
     * Data-buffers
     */
    public Vector<DataInput> data;

    /**
     * Create a new BluetoothOutput.
     *
     * @param idString        An identifier given by the experiment author used to group multiple devices and allow the user to distinguish them
     * @param deviceName      name of the device (can be null if deviceAddress is not null)
     * @param deviceAddress   address of the device (can be null if deviceName is not null)
     * @param uuidFilter       Optional filter to identify devices by advertices service or attribute UUIDs
     * @param autoConnect     If true, phyphox will not show a scan dialog and connect with the first matching device instead
     * @param context         context
     * @param buffers         list of dataOutputs to write the values
     * @param characteristics list of all characteristics the object should be able to operate on
     */
    public BluetoothOutput(String idString, String deviceName, String deviceAddress, UUID uuidFilter, Boolean autoConnect, Activity activity, Context context, Vector<DataInput> buffers, Vector<CharacteristicData> characteristics) {

        super(idString, deviceName, deviceAddress, uuidFilter, autoConnect, activity, context, characteristics);

        this.data = buffers;
    }


    /**
     * Write data to the Characteristics.
     */
    public void sendData() {
        if (!forcedBreak) {
            for (BluetoothGattCharacteristic characteristic : mapping.keySet()) {
                for (Characteristic c : mapping.get(characteristic)) {
                    if (data.get(c.index).getFilledSize() != 0) {
                        byte[] value = convertData(data.get(c.index).buffer, c.outputConversionFunction);
                        characteristic.setValue(value);
                        add(new WriteCommand(btGatt, characteristic));
                    }
                }
            }
        }
    }

    /**
     * Convert data using the specified conversion function.
     * Return a byte array with size 1 in case of an exception.
     *
     * @param data               data that should be converted
     * @param conversionFunction method to convert data (from ConversionsOutput)
     * @return the converted value
     */
    private byte[] convertData(DataBuffer data, ConversionsOutput.OutputConversion conversionFunction) {
        try {
            return conversionFunction.convert(data);
        } catch (Exception e) {
            return new byte[0]; // the method needs to return a byte array
        }
    }

} // end of class BluetoothOutput
