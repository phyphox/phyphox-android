package de.rwth_aachen.phyphox;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.StringBuilderPrinter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

//The bluetoothInput class encapsulates a generic serial input from bluetooth devices
public class bluetoothInput implements Serializable {
    public long period; //Sensor aquisition period in nanoseconds (inverse rate), 0 corresponds to as fast as possible
    public long t0 = 0; //the start time of the measurement. This allows for timestamps relative to the beginning of a measurement
    public Vector<dataOutput> data = new Vector<>(); //Data-buffers

    private long lastReading; //Remember the time of the last reading to fullfill the rate
    private Vector<Double> avg = new Vector<>(); //Used for averaging
    private boolean average = false; //Avergae over aquisition period?
    private Vector<Integer> aquisitions = new Vector<>(); //Number of aquisitions for this average

    private static final UUID btUUID = UUID.fromString("245fb312-a57f-40a1-9c45-9287984f270c");

    private Lock dataLock;

    private BluetoothAdapter btAdapter = null;
    BluetoothDevice btDevice = null;
    private BluetoothSocket btSocket = null;
    private InputStream inStream = null;

    private Protocol protocol;

    private AsyncReceive asyncReceive = null;

    public class bluetoothException extends Exception {
        public bluetoothException(String message) {
            super(message);
        }
    }

    protected bluetoothInput(String deviceName, String deviceAddress, double rate, boolean average, Vector<dataOutput> buffers, Lock lock, Protocol protocol) throws bluetoothException{
        this.protocol = protocol;

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
            this.aquisitions.add(0);
        }
    }

    public void openConnection() throws bluetoothException {
        if (btDevice != null) {
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
        for (int i = 0; i < avg.size(); i++) {
            avg.set(i, 0.);
            aquisitions.set(i, 0);
        }

        byte tempBuffer[] = new byte[1024];
        try {
            int remaining = inStream.available();
            while (remaining > 0) {
                inStream.read(tempBuffer);
                remaining = inStream.available();
            }
        } catch (IOException e) {
        }

        asyncReceive = new AsyncReceive();
        asyncReceive.execute();
    }

    //Stop the data aquisition
    public void stop() {
        if (asyncReceive != null) {
            asyncReceive.shutdown = true;
            asyncReceive.cancel(true);
        }
    }

    private class AsyncReceive extends AsyncTask<Void, Void, Void> {
        public boolean shutdown = false;
        private StringBuilder buffer = new StringBuilder();

        AsyncReceive() {
        }

        protected Void doInBackground(Void... params) {
            while (!shutdown && inStream != null) {
                byte tempBuffer[] = new byte[1024];
                try {
                    int readCount = inStream.read(tempBuffer, 0, 1024);
                    buffer.append(new String(tempBuffer).substring(0, readCount));
                    Vector<Vector<Double>> receivedData = protocol.receive(buffer);
                    if (receivedData == null || receivedData.size() == 0)
                        continue;
                    retrieveData(receivedData);
                } catch (Exception e) {

                }
            }
            return null;
        }

    }

    //This is called when we receive new data from a sensor. Append it to the right buffer
    public void retrieveData(Vector<Vector<Double>> receivedData) {
        long t = System.nanoTime();
        if (t0 == 0)
            t0 = t;

        Vector<Iterator<Double>> iterators = new Vector<>();
        for (Vector<Double> rd : receivedData) {
            iterators.add(rd.iterator());
        }

        boolean dataLeft = true;
        while (dataLeft) {
            dataLeft = false;
            for (int iBuffer = 0; iBuffer < receivedData.size() && iBuffer < avg.size(); iBuffer++) {
                if (iterators.get(iBuffer).hasNext()) {
                    dataLeft = true;
                    if (average) {
                        avg.set(iBuffer, avg.get(iBuffer) + iterators.get(iBuffer).next());
                        aquisitions.set(iBuffer, aquisitions.get(iBuffer) + 1);
                    } else {
                        avg.set(iBuffer, iterators.get(iBuffer).next());
                        aquisitions.set(iBuffer, 1);
                    }
                }
            }
        }

        if (lastReading + period <= t) {
            //Average/waiting period is over
            //Append the data to available buffers
            dataLock.lock();
            try {
                for (int i = 0; i < data.size(); i++) {
                    if (aquisitions.get(i) > 0)
                        data.get(i).append(avg.get(i) / aquisitions.get(i));
                }
            } finally {
                dataLock.unlock();
            }
            //Reset averaging
            for (int i = 0; i < data.size(); i++) {
                avg.set(i, 0.);
                aquisitions.set(i, 0);
            }

            this.lastReading = t;
        }
    }
}