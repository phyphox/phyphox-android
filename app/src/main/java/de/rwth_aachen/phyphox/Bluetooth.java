package de.rwth_aachen.phyphox;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.os.Handler;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class Bluetooth implements Serializable {

    transient private static BluetoothAdapter btAdapter = null;
    protected static Class conversions = (new Conversions()).getClass();

    transient BluetoothDevice btDevice = null;
    transient BluetoothGatt btGatt = null;
    protected String deviceName;
    protected String deviceAddress;

    protected Context context;
    protected OnExceptionRunnable handleException; // runs when an error occurs
    protected Handler mainHandler;

    private LinkedList<BluetoothCommand> commandQueue = new LinkedList<>(); // As Gatt can only process one command at a time, the commands are passed to a queue
    private boolean isExecuting = false; // true if a command is executed at the moment

    protected CountDownLatch cdl;

    protected boolean isRunning = false; // indicates whether the device has called start()
    protected boolean forcedBreak = false; // true if the experiment is running but the device disconnected

    protected int valuesSize = 0; // number of values that should be written (time not included)

    // maps all characteristics that have extra=time with the index of the buffer
    protected HashMap<BluetoothGattCharacteristic, Integer> saveTime = new HashMap<>();

    // each BluetoothGattCharacteristic that should be read maps with an array list of Characteristics
    protected HashMap<BluetoothGattCharacteristic, ArrayList<Characteristic>> mapping = new HashMap<>();
    protected static class Characteristic {
        public int index = -1; // index of the buffer the characteristic data should be saved in
        public Method conversionFunction = null; // conversion function for the value

        public Characteristic (int index, Method conversionFunction) {
            this.index = index;
            this.conversionFunction = conversionFunction;
        }
    }


    // Command on BluetoothGatt that should be queued
    protected abstract static class BluetoothCommand {
        BluetoothGatt gatt;
        BluetoothGattCharacteristic characteristic;

        public BluetoothCommand (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            this.gatt = gatt;
            this.characteristic = characteristic;
        }

        public BluetoothCommand (BluetoothGatt gatt) {
            this.gatt = gatt;
            this.characteristic = null;
        }

        public abstract boolean execute();

        public abstract String getErrorMessage();
    }


    protected static class WriteCommand extends BluetoothCommand {

        public WriteCommand (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super(gatt, characteristic);
        }

        @Override
        public boolean execute () {
            // TODO set write_type?
            return gatt.writeCharacteristic(characteristic);
        }

        @Override
        public String getErrorMessage() {
            return "Error on writing characteristic with Uuid "+characteristic.getUuid().toString()+".";
        }
    }


    protected static class ReadCommand extends BluetoothCommand {

        public ReadCommand (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super(gatt, characteristic);
        }

        @Override
        public boolean execute () {
            return gatt.readCharacteristic(characteristic);
        }

        @Override
        public String getErrorMessage() {
            return "Error on reading characteristic with Uuid "+characteristic.getUuid().toString()+".";
        }
    }


    protected static class DiscoverCommand extends BluetoothCommand {

        public DiscoverCommand (BluetoothGatt gatt) {
            super(gatt);
        }

        @Override
        public boolean execute() {
            return gatt.discoverServices();
        }

        @Override
        public String getErrorMessage() {
            return "Error on discovering the Services of the bluetooth device.";
        }
    }

    // Executes the next command in the queue if there is no command running and the queue is not empty
    protected void executeNext() {
        if (isExecuting) {
            return;
        }
        if (commandQueue.size() == 0) {
            return;
        }
        isExecuting = true;
        BluetoothCommand btC = commandQueue.poll();
        boolean result = btC.execute();
        if (!result) {
            isExecuting = false;
            handleException.setMessage(btC.getErrorMessage()+getDeviceData());
            mainHandler.post(handleException);
        }
        return;
    }

    // add a new command to the commandQueue and make sure it will be executed
    protected void add(BluetoothCommand command) {
        if (commandQueue.size() < (Math.max(10, 2 * mapping.size()))) { // allow only limited number of queued commands
            commandQueue.add(command);
        }
        executeNext();
    }

    // clears the commandQueue
    protected void clear() {
        commandQueue.clear();
        isExecuting = false;
    }

    // holds all data of a characteristic from a phyphox file
    public static class CharacteristicData {
        public UUID uuid;
        public UUID config_uuid = null; // characteristic whose value has to be changed
        public byte[] config_values; // value that has to be written to the characteristic with the uuid config_uuid
        public UUID period_uuid = null; // characteristic whose value has to be changed
        public byte[] period_values; // value that will be written to the characteristic with the uuid period_uuid to set the sensor measurement period
        public boolean extraTime; // true if the input has extra="time"
        public int index; // index of the buffer
        public Method conversionFunction = null;

        public CharacteristicData (UUID uuid, UUID config_uuid, byte[] config_values, UUID period_uuid, byte[] period_values, boolean extraTime, int index, String conversionFunctionName) throws BluetoothException {
            this.uuid = uuid;
            this.config_uuid = config_uuid;
            this.config_values = config_values;
            this.period_uuid = period_uuid;
            this.period_values = period_values;
            this.extraTime = extraTime;
            this.index = index;
            if (conversionFunctionName != null) {
                // test if the conversionFunction exists
                try {
                    this.conversionFunction = conversions.getDeclaredMethod(conversionFunctionName, new Class[]{byte[].class});
                    if (!Modifier.isPublic(this.conversionFunction.getModifiers())) {
                        throw new BluetoothException("The conversion \"" + conversionFunction + "\" does not exist.");
                    }
                } catch (NoSuchMethodException e) {
                    throw new BluetoothException("The conversion \"" + conversionFunction + "\" does not exist.");
                }
            }
        }
    }

    public static class BluetoothException extends Exception {
        public BluetoothException(String message) {
            super(message);
        }
    }

    public interface OnExceptionRunnable extends Runnable {
        void setMessage(String message);
    }


    // returns if bluetooth is enabled on the device or not
    public static boolean isEnabled() {
        if (btAdapter == null) {
            btAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        if (btAdapter == null) {
            return false;
        }
        if (!btAdapter.isEnabled()) {
            return false;
        }
        return true;
    }

    // returns a list of all paired BtLE devices
    public static ArrayList<BluetoothDevice> getPairedDevices () {
        ArrayList<BluetoothDevice> result = new ArrayList<>();
        for (BluetoothDevice b : btAdapter.getBondedDevices()) {
            int type = b.getType();
            if (type == BluetoothDevice.DEVICE_TYPE_DUAL || type == BluetoothDevice.DEVICE_TYPE_LE) { // only devices with Bluetooth LE
                result.add(b);
            }
        }
        return result;
    }




    public Bluetooth(String deviceName, String deviceAddress, Context context, Vector<CharacteristicData> characteristics) throws BluetoothException {
        this.deviceName = deviceName;
        this.deviceAddress = deviceAddress;

        this.context = context;
        this.mainHandler = new Handler(context.getMainLooper());
        findDevice();
        openConnection();

        for (CharacteristicData cd : characteristics) {
            this.addCharacteristic(cd);
        }
    }

    // returns the device data as a String to append it to Error-Messages
    public String getDeviceData () {
        String result = "On device with";
        if (deviceAddress != null) {
            result += " address \""+deviceAddress+"\"";
        }
        if (deviceName != null) {
            result += " name \""+deviceName+"\"";
        }
        result += ".";
        return result;
    }

    public List<BluetoothGattService> getServices() {
        return btGatt.getServices();
    }

    // Searches the device for the characteristic with the specified uuid
    protected BluetoothGattCharacteristic findCharacteristic (UUID uuid) throws BluetoothException {
        List<BluetoothGattService> services = btGatt.getServices();
        for (BluetoothGattService g : services) {
            List<BluetoothGattCharacteristic> characteristics = g.getCharacteristics();
            for (BluetoothGattCharacteristic c : characteristics) {
                if (uuid.equals(c.getUuid())) {
                    return c;
                }
            }
        }
        throw new BluetoothException("Characteristic with Uuid "+uuid+" not found. "+getDeviceData());
    }

    // adds a Characteristic to the list
    protected void addCharacteristic (CharacteristicData characteristic) throws BluetoothException {
        BluetoothGattCharacteristic c = findCharacteristic(characteristic.uuid);
        if (characteristic.extraTime) {
            saveTime.put(c, characteristic.index);
        } else {
            if (!mapping.containsKey(c)) {
                // Configure Characteristic if necessary
                if (characteristic.config_uuid != null) {
                    BluetoothGattCharacteristic config_c = findCharacteristic(characteristic.config_uuid);
                    if (config_c == null) {
                        throw new BluetoothException("Characteristic with Uuid " + characteristic.config_uuid + " not found. " + getDeviceData());
                    }
                    config_c.setValue(characteristic.config_values);
                    add(new WriteCommand(btGatt, config_c));
                }
                // Set the period to the specified value
                if (characteristic.period_uuid != null) {
                    BluetoothGattCharacteristic period_c = findCharacteristic(characteristic.period_uuid);
                    if (period_c == null) {
                        throw new BluetoothException("Characteristic with Uuid " + characteristic.period_uuid + " not found. "+getDeviceData());
                    }
                    period_c.setValue(characteristic.period_values);
                    add(new WriteCommand(btGatt, period_c));
                }
                // add Characteristic to the list for its BluetoothGattCharacteristic
                mapping.put(c, new ArrayList<Characteristic>());
            }
            Characteristic toAdd = new Characteristic(characteristic.index, characteristic.conversionFunction);
            valuesSize++;
            mapping.get(c).add(toAdd);
        }
    }

    // sets a Runnable that will be run in case of an exception
    public void setOnExceptionRunnable (OnExceptionRunnable exceptionRunnable) {
        this.handleException = exceptionRunnable;
    }

    // searches for the device with the specified name or address on the list of paired devices
    public void findDevice() throws BluetoothException {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null)
            throw new BluetoothException("Could not find a bluetooth adapter. Bluetooth is not supported on this hardware platform.");
        if (!btAdapter.isEnabled())
            throw new BluetoothException("Bluetooth is disabled. Please enable bluetooth and try again");
        // searches for the device
        for (BluetoothDevice d : getPairedDevices()) {
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
            throw new BluetoothException("Bluetooth device not found. (name filter: " + deviceName + ", address filter: " + deviceAddress+")");
    }

    // connects to the GATT Server hosted by the specified device
    public void openConnection() throws BluetoothException {
        boolean result = false;
        if (btDevice != null) {
            cdl = new CountDownLatch(1);
            btGatt = btDevice.connectGatt(context, true, btleGattCallback);
            try {
                // it should not be possible to continue before the device is connected
                // timeout after 5 seconds if the device could not be connected
                result = cdl.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
        }
        if (btGatt == null || !result) {
            throw new BluetoothException("Bluetooth connection could not be opened. "+getDeviceData());
        }
        cdl = new CountDownLatch(1); // new CountDownLatch with count 1
        add(new DiscoverCommand(btGatt));
        try {
            // it should not be possible to continue before the services are discovered
            // timeout after 5 seconds if the services could not be discovered
            result = cdl.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        if (!result) {
            throw new BluetoothException("Services could not be discovered. "+getDeviceData());
        }
    }

    public void reconnect() throws BluetoothException {
        if (btDevice == null)
            findDevice();
        if (btGatt == null)
            openConnection();
    }

    public void closeConnection() {
        if (btGatt != null) {
            btGatt.close();
        }
        clear();
    }

    //Check if the device is available without trying to use it.
    public boolean isAvailable() {
        return ((btDevice != null) && (btGatt != null));
    }

    // has to be overwritten by BluetoothInput / -Output
    public void start() {
        forcedBreak = false;
        isRunning = true;
    }
    public void stop() {
        isRunning = false;
    }

    // has to be overwritten if it should be able to receive data from the device
    protected void retrieveData(byte[] receivedData, BluetoothGattCharacteristic characteristic) {}
    protected void saveData(BluetoothGattCharacteristic characteristic, byte[] data) {}
    protected void retrieveData() {}

    // Callback functions for Gatt operations
    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS && handleException != null) {
                handleException.setMessage("The writing operation on a bluetooth device was not successful. " + getDeviceData());
                mainHandler.post(handleException);
            }
            isExecuting = false;
            executeNext();
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    if (forcedBreak) {
                        start(); // start collecting / sending data again
                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    // fall through to default
                default:
                    if (isRunning) {
                        forcedBreak = true;
                        stop(); // stop collecting / sending data
                    }
                    return;
            }
            if (cdl != null) {
                cdl.countDown();
            }
            isExecuting = false;
            executeNext();
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if (status != BluetoothGatt.GATT_SUCCESS && handleException != null) {
                handleException.setMessage("Discovering the characteristics on a bluetooth device was not successful. "+getDeviceData());
                mainHandler.post(handleException);

            }
            isExecuting = false;
            if (cdl != null) {
                cdl.countDown();
            }
            executeNext();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] data = null;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                data = characteristic.getValue();
            }
            isExecuting = false; // allow queue to execute new command before calling saveData
            executeNext();
            saveData(characteristic, data);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // retrieve data directly when the characteristic has changed
            byte[] data = characteristic.getValue();
            isExecuting = false; // allow queue to execute new command before retrieving data
            executeNext();
            retrieveData(data, characteristic);
        }

    };

}

