package de.rwth_aachen.phyphox;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

//TODO
//This is a first and very basic implementation. This class should support different protocols and
//allow to assign values from a single bluetooth device to different buffers. Also, for some
//protocols, a timestamp should be generated.
//Known bug: Already connected devices are not closed when the experiment is stopped, leading to a
//connection error, when the connection is reestablished after opening the experiment again.

//The bluetoothInput class encapsulates a generic serial input from bluetooth devices
public class bluetoothInput {
    public long period; //Sensor aquisition period in nanoseconds (inverse rate), 0 corresponds to as fast as possible
    public long t0 = 0; //the start time of the measurement. This allows for timestamps relative to the beginning of a measurement
    public Vector<dataBuffer> data = new Vector<>(); //Data-buffers

    private String buffer;
    private long lastReading; //Remember the time of the last reading to fullfill the rate
    private Vector<Double> avg = new Vector<>(); //Used for averaging
    private boolean average = false; //Avergae over aquisition period?
    private int aquisitions; //Number of aquisitions for this average

    private static final UUID btUUID = UUID.fromString("245fb312-a57f-40a1-9c45-9287984f270c");

    private Lock dataLock;

    private BluetoothAdapter btAdapter = null;
    BluetoothDevice btDevice = null;
    private BluetoothSocket btSocket = null;
    private InputStream inStream = null;

    public class bluetoothException extends Exception {
        public bluetoothException(String message) {
            super(message);
        }
    }

    protected bluetoothInput(String deviceName, String deviceAddress, double rate, boolean average, Vector<dataBuffer> buffers, Lock lock) throws bluetoothException{
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null)
            throw new bluetoothException("Could not find a bluetooth adapter.");
        if (!btAdapter.isEnabled())
            throw new bluetoothException("Bluetooth is disabled. Please enable bluetooth and try again");

        Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
        for (BluetoothDevice d : devices) {
            if (deviceAddress == null || deviceAddress.isEmpty()) {
                if (d.getName().equals(deviceName)) {
                    btDevice = d;
                    break;
                }
            } else {
                if (d.getAddress().equals(deviceAddress)) {
                    btDevice = d;
                    break;
                }
            }
        }

        if (btDevice == null)
            throw new bluetoothException("Bluetooth device not found. (name filter: " + deviceName + ", address filter: " + deviceAddress);

        this.dataLock = lock;

        if (rate <= 0)
            this.period = 0;
        else
            this.period = (long)((1/rate)*1e9); //Period in ns

        this.average = average;

        this.data = buffers;
        for (int i = 0; i < buffers.size(); i++) {
            this.avg.add(0.);
        }
    }

    public void openConnection() throws bluetoothException {
        if (btSocket != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    try {
                        btSocket = (BluetoothSocket) btDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(btDevice, 1);
                        //                    btSocket = btDevice.createInsecureRfcommSocketToServiceRecord(btUUID);
                    } catch (Exception e2) {
                        Log.e("phyphox", "Could not create insecure RfcommSocket for bluetooth device.");
                    }
                }
                if (btSocket == null)
                    btSocket = btDevice.createRfcommSocketToServiceRecord(btUUID);
            } catch (IOException e) {
                throw new bluetoothException("Could not create bluetooth socket.");
            }

            try {
                btSocket.connect();
            } catch (IOException e) {
                try {
                    btSocket.close();
                } catch (IOException e2) {

                }
                throw new bluetoothException("Could not open bluetooth connection: " + e.getMessage());
            }
        }

        if (btSocket == null)
            throw new bluetoothException("Bluetooth connection opened successfully, but socket is null.");

        try {
            inStream = btSocket.getInputStream();
        } catch (IOException e) {
            throw new bluetoothException("Could get input stream from bluetooth device.");
        }
    }

    public void closeConnection() {
        try {
            inStream.close();
        } catch (Exception e) {

        } finally {
            inStream = null;
        }

        try {
            btSocket.close();
        } catch (Exception e) {

        } finally {
            btSocket = null;
        }
    }

    //Check if the sensor is available without trying to use it.
    public boolean isAvailable() {
        return (btDevice != null);
    }

    //Start the data aquisition
    public void start() {
        this.t0 = 0; //Reset t0. This will be set by the first sensor event

        //Reset averaging
        this.lastReading = 0;
        for (int i = 0; i < avg.size(); i++)
            avg.set(i, 0.);
        this.aquisitions = 0;

        byte tempBuffer[] = new byte[1024];
        try {
            int remaining = inStream.available();
            while (remaining > 0) {
                inStream.read(tempBuffer);
                remaining = inStream.available();
            }
        } catch (IOException e) {
        }

        buffer = "";
    }

    //Stop the data aquisition
    public void stop() {

    }

    //This is called when we receive new data from a sensor. Append it to the right buffer
    public void retrieveData() {
        long t = System.nanoTime();
        if (t0 == 0)
            t0 = t;

        try {
            byte tempBuffer[] = new byte[256];
            int remaining = inStream.available();
            int readCount;
            while (remaining > 0) {
                readCount = inStream.read(tempBuffer, 0, 256);
                buffer += (new String(tempBuffer)).substring(0, readCount);
                remaining = inStream.available();
            }
        } catch (IOException e) {
        }

        int separator = buffer.indexOf(";");
        while (separator >= 0) {
            double v = Double.NaN;
            try {
                v = Double.valueOf(buffer.substring(0, separator));
            } catch (NumberFormatException e) {
                buffer = buffer.substring(separator+1);
                separator = buffer.indexOf(";");
                continue;
            }

            buffer = buffer.substring(separator);

            if (average) {
                //TODO: More complex protocols: Asign values to buffers
                for (int i = 0; i < avg.size(); i++)
                    avg.set(i, avg.get(i) + v);
                aquisitions++;
            } else {
                //TODO: More complex protocols: Asign values to buffers
                for (int i = 0; i < avg.size(); i++)
                    avg.set(i, v);
                aquisitions = 1;
            }
            if (lastReading + period <= t) {
                //Average/waiting period is over
                //Append the data to available buffers
                dataLock.lock();
                //TODO: More complex protocols: Asign values to buffers (some protocols should also generate a timestamp)
                try {
                    for (int i = 0; i < data.size(); i++)
                        data.get(i).append(avg.get(i) / aquisitions);
                } finally {
                    dataLock.unlock();
                }
                //Reset averaging
                this.lastReading = t;
                for (int i = 0; i < avg.size(); i++)
                    avg.set(i, 0.);
                this.aquisitions = 0;
            }

            separator = buffer.indexOf(";");
        }
    }
}