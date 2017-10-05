package de.rwth_aachen.phyphox;


import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.HashMap;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

import static java.lang.Math.pow;


@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
//The BluetoothInput class encapsulates a generic serial input to bluetooth devices
public class BluetoothInput extends Bluetooth {

    private long period; //Sensor aquisition period in nanoseconds (inverse rate), 0 corresponds to as fast as possible
    private long t0 = 0; //the start time of the measurement. This allows for timestamps relative to the beginning of a measurement
    private Vector<dataOutput> data = new Vector<>(); //Data-buffers
    private String mode; // "poll" or "notification"
    private long lastReading; //Remember the time of the last reading to fullfill the rate
    private Vector<Double> avg = new Vector<>(); //Used for averaging
    private boolean average = false; //Average over aquisition period?
    private Vector<Integer> aquisitions = new Vector<>(); //Number of aquisitions for this average
    private Lock dataLock;


    public BluetoothInput(String deviceName, String deviceAddress, String mode, double rate, boolean average, Vector<dataOutput> buffers, Lock lock, Context context, Vector<CharacteristicData> characteristics)
            throws BluetoothException {

        super(deviceName, deviceAddress, context, characteristics);

        this.mode = mode.toLowerCase();

        if (mode.equals("poll") && rate < 0) {
            throw new BluetoothException("Invalid value for \"rate\". "+getDeviceData());
        }

        this.dataLock = lock;

        if (rate <= 0)
            this.period = 0; // as fast as possible
        else
            this.period = (long)((1/rate)*1e9); //period in ns

        this.average = average;

        this.data = buffers;
        for (int i = 0; i < buffers.size(); i++) {
            this.avg.add(0.);
            this.aquisitions.add(0);
        }

    }

    //Start the data acquisition
    public void start() {
        if (!isAvailable()) {
            try {
                reconnect();
            } catch (BluetoothException e) {
                handleException.setMessage(e.getMessage());
                mainHandler.post(handleException);
            }
        }
        receivedData = new HashMap<>();
        this.t0 = 0; //Reset t0. This will be set by the first sensor event

        //Reset averaging
        this.lastReading = 0;
        resetAveraging();

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
                        handleException.setMessage(e.getMessage());
                        mainHandler.post(handleException);
                    }
                }
                break;
            }
            default: {
                handleException.setMessage("Unknown mode for bluetooth Input. "+getDeviceData());
                mainHandler.post(handleException);
            }

        }

    }

    //Stop the data acquisition
    public void stop() throws BluetoothException {
        receivedData = null;
        switch (mode) {
            case "poll": {
                if (mainHandler != null) {
                    mainHandler.removeCallbacksAndMessages(null);
                }
                break;
            }
            case "notification": {
                for (BluetoothGattCharacteristic c : mapping.keySet()) {
                    setCharacteristicNotification(c, false);
                }
                break;
            }
        }

        // reset commandQueue
        clear();

    }


    protected void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) throws BluetoothException {
        if (!isAvailable()) {
            return; // the error will already be displayed if there is no connection
        }
        btGatt.setCharacteristicNotification(characteristic, enable);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")); // Descriptor for Client Characteristic Configuration
        if (descriptor == null) {
            if (enable) {
                throw new BluetoothException("The characteristic notification could not be enabled. "+getDeviceData());
            } else {
                throw new BluetoothException("The characteristic notification could not be disabled again. "+getDeviceData());
            }
        }
        descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        boolean result = btGatt.writeDescriptor(descriptor); //descriptor write operation successfully initiated?
        if (!result) {
            throw new BluetoothException("Turning on notifications for the Characteristic with the Uuid " + characteristic.getUuid().toString() + " was not successful. " + getDeviceData());
        }
    }

    @Override
    // retrieve Data from all characteristics (mode poll)
    protected void retrieveData() {
        long t = System.nanoTime();

        for (Characteristic c : mapping.values()) {
            if (t0 == 0 && c.timeIndex >= 0 && data.get(c.timeIndex) != null && data.get(c.timeIndex).getFilledSize() > 0) {
                t0 = t;
                t0 -= data.get(c.timeIndex).getValue() * 1e9;
            }
            double output = Double.NaN; // default value if we did not receive data from the characteristic
            if (receivedData.get(c) != null && receivedData.get(c).length != 0) {
                /*int mantissa;
                int exponent;
                Integer lowerByte = (int) receivedData.get(c)[0] & 0xFF;
                Integer upperByte = (int) receivedData.get(c)[1] & 0xFF;
                Integer sfloat= (upperByte << 8) + lowerByte;
                mantissa = sfloat & 0x0FFF;
                exponent = (sfloat >> 12) & 0xFF;
                double magnitude = pow(2.0f, exponent);
                output = (mantissa * magnitude) / 100.0f;*/
                output = receivedData.get(c)[0]; //TODO
            }
            setAvgAndAquisitions(c, output);
        }

        if (t0 == 0)
            t0 = t;

        receivedData.clear(); // remove values

        if (lastReading + period <= t) {
            //Average/waiting period is over
            //Append the data to available buffers
            dataLock.lock();
            try {
                for (int i = 0; i < data.size(); i++) {
                    if (aquisitions.get(i) > 0) {
                        data.get(i).append(avg.get(i) / aquisitions.get(i));
                    }
                }
                for (Characteristic c : mapping.values()) {
                    if (c.timeIndex >= 0) {
                        double time = (t-t0) / 1e9;
                        data.get(c.timeIndex).append(time);
                    }
                }
            } finally {
                dataLock.unlock();
            }

            resetAveraging();

            this.lastReading = t;
        }
    }


    @Override
    // retrieve data from one characteristic (mode notification)
    protected void retrieveData(byte[] receivedData, Characteristic characteristic) {
        long t = System.nanoTime();
        if (t0 == 0) {
            t0 = t;
            if (characteristic.timeIndex >= 0 && data.get(characteristic.timeIndex) != null && data.get(characteristic.timeIndex).getFilledSize() > 0)
                t0 -= data.get(characteristic.timeIndex).getValue() * 1e9;
        }

        /*int mantissa;
        int exponent;
        Integer lowerByte = (int) receivedData[0] & 0xFF;
        Integer upperByte = (int) receivedData[1] & 0xFF;
        Integer sfloat= (upperByte << 8) + lowerByte;
        mantissa = sfloat & 0x0FFF;
        exponent = (sfloat >> 12) & 0xFF;
        double magnitude = pow(2.0f, exponent);
        double output = (mantissa * magnitude) / 100.0f;*/
        double output = receivedData[0]; // TODO

        setAvgAndAquisitions(characteristic, output);

        if (lastReading + period <= t) {
            //Average/waiting period is over
            //Append the data to available buffers
            dataLock.lock();
            try {
                if (aquisitions.get(characteristic.index) > 0) {
                    data.get(characteristic.index).append(avg.get(characteristic.index) / aquisitions.get(characteristic.index));
                    if (characteristic.timeIndex >= 0) {
                        double time = ((t-t0)/1e9);
                        data.get(characteristic.timeIndex).append(time);
                    }
                }
            } finally {
                dataLock.unlock();
            }

            resetAveraging();

            this.lastReading = t;
        }
    }

    // sets avg and aquisitions for the characteristic to the value data
    private void setAvgAndAquisitions (Characteristic characteristic, double data) {
        if (characteristic.index >= 0) {
            if (average) {
                avg.set(characteristic.index, avg.get(characteristic.index) + data);
                aquisitions.set(characteristic.index, aquisitions.get(characteristic.index) + 1);
            } else {
                avg.set(characteristic.index, data);
                aquisitions.set(characteristic.index, 1);
            }
        }
    }

    // resets all values in avg and aquisitions to 0
    private void resetAveraging () {
        for (int i = 0; i < data.size(); i++) {
            avg.set(i, 0.);
            aquisitions.set(i, 0);
        }
    }
}

