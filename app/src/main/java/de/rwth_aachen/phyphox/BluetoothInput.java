package de.rwth_aachen.phyphox;


import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

import static java.lang.Math.pow;


@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
//The BluetoothInput class encapsulates a generic serial input to bluetooth devices
public class BluetoothInput extends Bluetooth {

    protected static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); // Descriptor for Client Characteristic Configuration

    private long period; //Sensor aquisition period in nanoseconds (inverse rate), 0 corresponds to as fast as possible
    private long t0 = 0; //the start time of the measurement. This allows for timestamps relative to the beginning of a measurement
    private Vector<dataOutput> data = new Vector<>(); //Data-buffers
    private String mode; // "poll" or "notification"
    private Lock dataLock;
    protected HashMap<Integer, Double> outputs;


    public BluetoothInput(String deviceName, String deviceAddress, String mode, double rate, Vector<dataOutput> buffers, Lock lock, Context context, Vector<CharacteristicData> characteristics)
            throws BluetoothException {

        super(deviceName, deviceAddress, context, characteristics);

        this.mode = mode.toLowerCase();

        if (mode.equals("poll") && rate < 0) {
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_rate)+" "+getDeviceData());
        }

        this.dataLock = lock;

        if (rate <= 0)
            this.period = 0; // as fast as possible
        else
            this.period = (long)((1/rate)*1e9); //period in ns

        this.data = buffers;
    }

    //Start the data acquisition
    @Override
    public void start() {
        super.start();
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
        outputs = new HashMap<>();
        this.t0 = 0; //Reset t0. This will be set by the first sensor event

        switch (mode) {
            case "poll": {
                final Runnable readData = new Runnable() {
                  @Override
                    public void run () {
                      // read data from all characteristics
                      for (BluetoothGattCharacteristic c : mapping.keySet()) {
                          add(new ReadCommand(btGatt, c));
                      }
                      mainHandler.postDelayed(this, period / 1000000); // poll data again after the period is over
                  }
                };
                mainHandler.post(readData);
                break;
            }
            case "notification": {
                // turn on characteristic notification for each characteristic
                for (BluetoothGattCharacteristic c : mapping.keySet()) {
                    try {
                        setCharacteristicNotification(c, true);
                    } catch (BluetoothException e) {
                        if (handleException != null) {
                            handleException.setMessage(e.getMessage());
                            mainHandler.post(handleException);
                        }
                    }
                }
                break;
            }
            default: {
                if (handleException != null) {
                    handleException.setMessage(context.getResources().getString(R.string.bt_exception_mode) + " " + getDeviceData());
                    mainHandler.post(handleException);
                }
            }

        }

    }

    //Stop the data acquisition
    @Override
    public void stop() {
        super.stop();
        switch (mode) {
            case "poll": {
                if (mainHandler != null) {
                    mainHandler.removeCallbacksAndMessages(null);
                }
                break;
            }
            case "notification": {
                for (BluetoothGattCharacteristic c : mapping.keySet()) {
                    try {
                        setCharacteristicNotification(c, false);
                    } catch (BluetoothException e) {
                        //TODO
                    }
                }
                break;
            }
        }

        // reset commandQueue
        clear();

    }


    protected void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) throws BluetoothException {
        if (!isAvailable()) {
            return; // TODO
        }
        btGatt.setCharacteristicNotification(characteristic, enable);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
        if (descriptor == null) {
            if (enable) {
                throw new BluetoothException(context.getResources().getString(R.string.bt_exception_notification)+" "+characteristic.getUuid().toString()+" "+context.getResources().getString(R.string.bt_exception_notification_enable)+" "+getDeviceData());
            } else {
                throw new BluetoothException(context.getResources().getString(R.string.bt_exception_notification)+" "+characteristic.getUuid().toString()+" "+context.getResources().getString(R.string.bt_exception_notification_disable)+" "+getDeviceData());
            }
        }
        descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        boolean result = btGatt.writeDescriptor(descriptor); //descriptor write operation successfully initiated?
        if (!result && enable) {
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_notification_fail_enable)+" " + characteristic.getUuid().toString() + " "+context.getResources().getString(R.string.bt_exception_notification_fail)+" "+ getDeviceData());
        } else if (!result && !enable) {
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_notification_fail_disable)+" "  + characteristic.getUuid().toString() + " "+context.getResources().getString(R.string.bt_exception_notification_fail)+" " + getDeviceData());
        }
    }

    @Override
    // saves data from the characteristic in outputs and calls retrieveData it is time
    protected void saveData (BluetoothGattCharacteristic characteristic, byte[] data) {
        if (outputs != null) {
            for (Characteristic c : mapping.get(characteristic)) {
                // call retrieveData if the data from this characteristic is already stored
                if (outputs.containsKey(c.index)) {
                    retrieveData();
                    return;
                }
                // convert data and add it to outputs if it was read successfully
                if (data != null) {
                    outputs.put(c.index, convertData(data, c.conversionFunction));
                }
            }
            // call retrieveData if data from every characteristic is received
            if (outputs.size() == valuesSize) {
                retrieveData();
            }
        }
    }

    @Override
    // retrieve Data from all characteristics (mode poll)
    protected void retrieveData() {
        long t = System.nanoTime();

        // set t0 if it is not yet set
        if (t0 == 0) {
            t0 = t;
            for (Integer i : saveTime.values()) {
                dataOutput dataOutput = data.get(i);
                if (dataOutput != null && dataOutput.getFilledSize() > 0) {
                    t0 -= dataOutput.getValue() * 1e9;
                    break;
                }
            }
        }

        //Append the data to available buffers
        dataLock.lock();
        try {
            for (ArrayList<Characteristic> al : mapping.values()) {
                for (Characteristic c : al) {
                    data.get(c.index).append(outputs.get(c.index));
                }
            }
            // append time to buffers
            for (Integer i : saveTime.values()) {
                data.get(i).append((t - t0) / 1e9);
            }
        } finally {
            dataLock.unlock();
            outputs.clear(); // remove values from receivedData because it is retrieved now
        }
    }


    @Override
    // retrieve data from one characteristic (mode notification)
    protected void retrieveData(byte[] receivedData, BluetoothGattCharacteristic characteristic) {
        ArrayList<Characteristic> characteristics = mapping.get(characteristic);
        long t = System.nanoTime();
        // set t0 if it is not yet set
        if (t0 == 0) {
            t0 = t;
            for (Integer i : saveTime.values()) {
                dataOutput dataOutput = data.get(i);
                if (dataOutput != null && dataOutput.getFilledSize() > 0) {
                    t0 -= dataOutput.getValue() * 1e9;
                    break;
                }
            }
        }
        double[] outputs = new double[characteristics.size()];
        for (Characteristic c : characteristics) {
            outputs[characteristics.indexOf(c)] = convertData(receivedData, c.conversionFunction);
        }

        //Append the data to available buffers
        dataLock.lock();
        try {
            for (Characteristic c : characteristics) {
                data.get(c.index).append(outputs[characteristics.indexOf(c)]);
            }
            // append time to buffer if extra=time is set
            if (saveTime.containsKey(characteristic)) {
                data.get(saveTime.get(characteristic)).append((t - t0) / 1e9);
            }
        } finally {
            dataLock.unlock();
        }
    }

    // converts a byte array to a double value with the specified conversion function.
    // the method also handles exceptions by running handleException
    private double convertData(byte[] data, Method conversionFunction) {
        try {
            return (double) conversionFunction.invoke(null, data);
        } catch (Exception e) {
            if (handleException != null) {
                handleException.setMessage(context.getResources().getString(R.string.bt_exception_conversion3)+" \"" + conversionFunction + "\". " + getDeviceData());
                mainHandler.post(handleException);
            }
        }
        return Double.NaN; // the method needs to return a double value
    }

}

