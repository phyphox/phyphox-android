package de.rwth_aachen.phyphox;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The Bluetooth class encapsulates a generic Bluetooth connection and deals with the following tasks:
 * <ul>
 * <li>connecting a device</li>
 * <li>operating on characteristics</li>
 * <li>queueing the commands on the BluetoothGatt object</li>
 * <li>giving opportunities to display error messages</li>
 * <li>closing the connection</li>
 * </ul>
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class Bluetooth implements Serializable {

    transient private static BluetoothAdapter btAdapter;
    public static OnExceptionRunnable errorDialog = new OnExceptionRunnable();

    protected transient BluetoothDevice btDevice;
    protected transient BluetoothGatt btGatt;
    private String deviceName;
    private String deviceAddress;

    /**
     * holds data to all characteristics to add or configure once the device is connected
     */
    protected Vector<CharacteristicData> characteristics;
    /**
     * number of values from characteristics that should be written
     */
    protected int valuesSize;
    /**
     * maps all characteristics that have extra=time with the index of the buffer
     */
    protected HashMap<BluetoothGattCharacteristic, Integer> saveTime = new HashMap<>();
    /**
     * each BluetoothGattCharacteristic that should be read maps with an array list of Characteristics
     */
    protected HashMap<BluetoothGattCharacteristic, ArrayList<Characteristic>> mapping = new HashMap<>();
    /**
     * used for important asynchronous tasks e.g. connectGatt
     */
    protected CancellableLatch cdl;
    /**
     * indicates whether the experiment is running or not
     */
    protected boolean isRunning;
    /**
     * true if the experiment is running but the device disconnected
     */
    protected boolean forcedBreak;
    /**
     * queue for the Bluetooth Gatt commands
     */
    private LinkedList<BluetoothCommand> commandQueue;
    /**
     * indicates whether a command is being executed at the moment
     */
    private Boolean isExecuting;

    protected Context context;
    /**
     * used to display errors while the experiment is running
     */
    protected Toast toast;
    /**
     * is able to change UI and is used to display errors
     */
    protected Handler mainHandler;


    /**
     * Return true if Bluetooth Low Energy is supported on the device.
     *
     * @param context context
     * @return true if Bluetooth Low Energy is supported
     */
    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * Return true if Bluetooth is enabled on the device.
     *
     * @return true if Bluetooth is enabled
     */
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

    /**
     * Return a list of all paired Bluetooth Low Energy devices.
     *
     * @return list of paired Bluetooth Low Energy devices.
     */
    public static Vector<BluetoothDevice> getPairedDevices() {
        Vector<BluetoothDevice> result = new Vector<>();
        for (BluetoothDevice b : btAdapter.getBondedDevices()) {
            int type = b.getType();
            if (type == BluetoothDevice.DEVICE_TYPE_DUAL || type == BluetoothDevice.DEVICE_TYPE_LE) { // only devices with Bluetooth LE
                result.add(b);
            }
        }
        return result;
    }

    /**
     * Create a new Bluetooth object.
     *
     * @param deviceName      name of the device (can be null if deviceAddress is not null)
     * @param deviceAddress   address of the device (can be null if deviceName is not null)
     * @param context         context
     * @param characteristics list of all characteristics the object should be able to operate on
     */
    public Bluetooth(String deviceName, String deviceAddress, Context context, Vector<CharacteristicData> characteristics) {
        this.deviceName = deviceName;
        this.deviceAddress = deviceAddress;

        this.context = context;
        this.mainHandler = new Handler(this.context.getMainLooper());
        // create toast to show error messages while the experiment is running
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                toast = Toast.makeText(Bluetooth.this.context, Bluetooth.this.context.getResources().getString(R.string.bt_default_error_message), Toast.LENGTH_LONG);
            }
        });

        this.characteristics = characteristics;
        isRunning = false;
        forcedBreak = false;
        valuesSize = 0;
        commandQueue = new LinkedList<>();
        isExecuting = false;
    }

    /**
     * Return true if the device is connected.
     *
     * @return true if the device is connected.
     */
    public boolean isConnected() {
        if (btAdapter == null)
            return false;
        if (!btAdapter.isEnabled())
            return false;
        return ((BluetoothManager) context.getSystemService(context.BLUETOOTH_SERVICE)).getConnectedDevices(BluetoothProfile.GATT).contains(btDevice);
    }

    /**
     * Connects with the device.
     *
     * @throws BluetoothException if there is an error on findDevice, openConnection or process CharacteristicData
     */
    public void connect() throws BluetoothException {
        if (btDevice == null) {
            findDevice();
        }
        // check if a device was found and if it is already connected
        if (btGatt == null || !isConnected()) {
            openConnection();
        }
        if (!isConnected()) {
            btGatt.connect();
        }
        mapping.clear(); // clear mapping so it won't contain a characteristic twice
        // add characteristics and do the config
        for (CharacteristicData cd : characteristics) {
            cd.process(this);
        }
    }

    /**
     * Searches for the device with the specified name or address on the list of paired devices.
     *
     * @throws BluetoothException if Bluetooth is disabled or if the device could not be found
     */
    public void findDevice() throws BluetoothException {
        if (!isEnabled()) {
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_disabled), this);
        }
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
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_notfound), this);
    }

    /**
     * Connects to the Gatt Server hosted by the specified device and discovers its Services.
     *
     * @throws BluetoothException if Bluetooth is not enabled, if the connection could not be opened or if the services could not be discovered
     */
    protected void openConnection() throws BluetoothException {
        if (!isEnabled()) {
            if (btGatt != null) {
                btGatt.close();
                btGatt = null;
            }
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_disabled), this);
        }
        boolean result = false;

        cdl = new CancellableLatch(1);
        btGatt = btDevice.connectGatt(context, true, btLeGattCallback);
        try {
            // it should not be possible to continue before the device is connected
            // timeout after 5 seconds if the device could not be connected
            result = cdl.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        // throw exception if it was not possible to connect
        if (btGatt == null || !result) {
            if (btGatt != null) {
                btGatt.close();
            }
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_connection), this);
        }

        // try discover services once the device is connected
        cdl = new CancellableLatch(1);
        add(new DiscoverCommand(btGatt));
        try {
            // it should not be possible to continue before the services are discovered
            // timeout after 5 seconds if the services could not be discovered
            result = cdl.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        if (!result) {
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_services), this);
        }
    }

    /**
     * Closes the connection to the Gatt server of the device.
     */
    public void closeConnection() {
        if (btGatt != null && isConnected()) {
            btGatt.close();
        }
        clear();
        synchronized (isExecuting) {
            isExecuting = false;
        }
    }

    /**
     * Called when the experiment is started.
     *
     * @throws BluetoothException if the device is not connected
     */
    public void start() throws BluetoothException {
        if (!isConnected()) {
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_no_connection), this);
        }
        forcedBreak = false;
        isRunning = true;
    }

    /**
     * Called when the experiment is stopped.
     */
    public void stop() {
        toast.cancel();
        clear(); // reset commandQueue
        isRunning = false;
    }

    /**
     * Called when there was a notification that the value of a BluetoothGattCharacteristic has changed.
     *
     * @param data           data read from the characteristic.
     * @param characteristic characteristic that got the notification.
     */
    protected void retrieveData(byte[] data, BluetoothGattCharacteristic characteristic) {
    }

    /**
     * Called when a BluetoothGattCharacteristic was read.
     *
     * @param data           data read from the characteristic.
     * @param characteristic characteristic that was read.
     */
    protected void saveData(byte[] data, BluetoothGattCharacteristic characteristic) {
    }

    /**
     * Searches the device for the specified BluetoothGattCharacteristic.
     *
     * @param uuid UUID of the BluetoothGattCharacteristic
     * @return the Characteristic
     * @throws BluetoothException if the BluetoothGattCharacteristic could not be found
     */
    protected BluetoothGattCharacteristic findCharacteristic(UUID uuid) throws BluetoothException {
        List<BluetoothGattService> services = btGatt.getServices();
        for (BluetoothGattService g : services) {
            List<BluetoothGattCharacteristic> characteristics = g.getCharacteristics();
            for (BluetoothGattCharacteristic c : characteristics) {
                if (uuid.equals(c.getUuid())) {
                    return c;
                }
            }
        }
        throw new BluetoothException(context.getResources().getString(R.string.bt_exception_uuid) + " " + uuid.toString() + " " + context.getResources().getString(R.string.bt_exception_uuid2), this);
    }

    /**
     * Displays the right error message (toast if the experiment is running, errorDialog if not)
     *
     * @param message will be displayed
     */
    protected void displayErrorMessage(final String message) {
        displayErrorMessage(message, !isRunning);
    }

    /**
     * Displays an error message.
     *
     * @param message    will be displayed
     * @param showDialog displays message in an AlertDialog if true and in a Toast if false
     */
    protected void displayErrorMessage(final String message, boolean showDialog) {
        if (showDialog) {
            // show Error Dialog
            errorDialog.message = message;
            mainHandler.post(errorDialog);
        } else {
            // show toast
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    toast.setText(message);
                    toast.show();
                }
            });
        }
    }

    /**
     * Executes the next command in the queue if there is no command running and the queue is not empty.
     * The method displays an error message if the command could not be started.
     */
    protected void executeNext() {
        if (commandQueue.size() == 0) {
            return;
        }
        synchronized (isExecuting) {
            if (isExecuting) {
                return;
            }
            isExecuting = true;
        }
        BluetoothCommand btC = commandQueue.poll();
        boolean result = btC.execute();
        if (!result) {
            synchronized (isExecuting) {
                isExecuting = false;
            }
            // give notice of the error by cancelling the latch or displaying the error message
            if (cdl.getCount() > 0) {
                cdl.cancel();
            } else {
                displayErrorMessage(btC.getErrorMessage());
            }
        }
    }

    /**
     * Adds a new command to the commandQueue and make sure that it will be executed.
     * There is a limit of commands that can be stored in the queue.
     *
     * @param command
     */
    protected void add(BluetoothCommand command) {
        if (commandQueue.size() < (Math.max(10, 2 * mapping.size()))) { // allow only limited number of queued commands
            commandQueue.add(command);
        }
        executeNext();
    }

    /**
     * Clears the command queue.
     */
    protected void clear() {
        commandQueue.clear();
    }


    // Callback functions for Gatt operations
    /**
     * Callback functions for Gatt operations.
     * The methods allow the queue to execute the next command, display errors and make sure the read data is retrieved.
     * The CancellableLatch is also counted down here.
     */
    private final BluetoothGattCallback btLeGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            synchronized (isExecuting) {
                isExecuting = false;
            }
            executeNext();
            // retrieve data directly when the characteristic has changed
            byte[] data = characteristic.getValue();
            retrieveData(data, characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                String errorMessage = context.getResources().getString(R.string.bt_fail_reading);
                if (deviceName != null) {
                    errorMessage += context.getResources().getString(R.string.bt_exception_device_name) + " " + deviceName;
                } else {
                    errorMessage += context.getResources().getString(R.string.bt_exception_device_address) + " " + deviceAddress;
                }
                displayErrorMessage(errorMessage + ")");
            }
            synchronized (isExecuting) {
                isExecuting = false;
            }
            executeNext();
            byte[] data = null;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                data = characteristic.getValue();
            }
            saveData(data, characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cdl.countDown();
            } else {
                if (isRunning) {
                    String errorMessage = context.getResources().getString(R.string.bt_fail_writing);
                    if (deviceName != null) {
                        errorMessage += context.getResources().getString(R.string.bt_exception_device_name) + " " + deviceName;
                    } else {
                        errorMessage += context.getResources().getString(R.string.bt_exception_device_address) + " " + deviceAddress;
                    }
                    displayErrorMessage(errorMessage + ")");
                } else {
                    cdl.cancel();
                }
            }
            synchronized (isExecuting) {
                isExecuting = false;
            }
            executeNext();
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cdl.countDown();
            } else {
                cdl.cancel();
            }
            synchronized (isExecuting) {
                isExecuting = false;
            }
            executeNext();
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (isRunning) {
                            try {
                                start(); // start collecting / sending data again if the experiment is running
                            } catch (BluetoothException e) {
                                // means that the device disconnected so error handling will come anyway
                            }
                        }
                    }
                    break;
                // fall through to default if status was not GATT_SUCCESS
                case BluetoothProfile.STATE_DISCONNECTED:
                    // fall through to default
                default:
                    if (isRunning) {
                        stop(); // stop collecting data
                        forcedBreak = true;
                        isRunning = true; // the experiment is still running, only bluetooth has stopped
                        String errorMessage = context.getResources().getString(R.string.bt_exception_disconnected) + " (";
                        if (deviceName != null) {
                            errorMessage += context.getResources().getString(R.string.bt_exception_device_name) + " " + deviceName;
                        } else {
                            errorMessage += context.getResources().getString(R.string.bt_exception_device_address) + " " + deviceAddress;
                        }
                        displayErrorMessage(errorMessage + ")");
                        (new ReconnectBluetoothTask()).execute(); // try to reconnect the device
                    }
                    return;
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cdl.countDown();
            } else {
                cdl.cancel();
            }
            synchronized (isExecuting) {
                isExecuting = false;
            }
            executeNext();
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cdl.countDown();
            } else {
                cdl.cancel();
            }
            synchronized (isExecuting) {
                isExecuting = false;
            }
            executeNext();
        }
    }; // end of BluetoothGattCallback


    // CountDownLatch that can be cancelled. Used for important gatt-commands.
    protected static class CancellableLatch extends CountDownLatch {
        private boolean cancelled;

        public CancellableLatch(int count) {
            super(count);
            cancelled = false;
        }

        @Override
        // returns true if the countDown was called before the timeout but only if it was not cancelled
        public boolean await(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
            boolean result = super.await(timeout, unit);
            return !cancelled && result;
        }

        // counts down the latch but await will return false
        public void cancel() {
            cancelled = true;
            while (getCount() > 0) {
                countDown();
            }
        }
    } // end of class CancellableLatch


    protected static class Characteristic {
        public int index = -1; // index of the buffer the characteristic data should be saved in
        public Method conversionFunction = null; // conversion function for the value

        public Characteristic(int index, Method conversionFunction) {
            this.index = index;
            this.conversionFunction = conversionFunction;
        }
    } // end of class Characteristic


    // Command on BluetoothGatt that should be queued
    protected abstract class BluetoothCommand {
        BluetoothGatt gatt;
        BluetoothGattCharacteristic characteristic;

        public BluetoothCommand(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            this.gatt = gatt;
            this.characteristic = characteristic;
        }

        public BluetoothCommand(BluetoothGatt gatt) {
            this.gatt = gatt;
            this.characteristic = null;
        }

        public abstract boolean execute();

        public abstract String getErrorMessage();
    } // end of class BluetoothCommand


    protected class WriteCommand extends BluetoothCommand {

        public WriteCommand(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super(gatt, characteristic);
        }

        @Override
        public boolean execute() {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            return gatt.writeCharacteristic(characteristic);
        }

        @Override
        public String getErrorMessage() {
            return context.getResources().getString(R.string.bt_error_writing) + " " + characteristic.getUuid().toString() + ".";
        }
    } // end of class WriteCommand


    protected class WriteDescriptorCommand extends BluetoothCommand {
        private BluetoothGattDescriptor descriptor;

        public WriteDescriptorCommand(BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
            super(gatt);
            this.descriptor = descriptor;
        }

        @Override
        public boolean execute() {
            return gatt.writeDescriptor(descriptor);
        }

        @Override
        public String getErrorMessage() {
            return "";
        } // error message will not be displayed
    } // end of class WriteDescriptorCommand


    protected class ReadCommand extends BluetoothCommand {

        public ReadCommand(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super(gatt, characteristic);
        }

        @Override
        public boolean execute() {
            return gatt.readCharacteristic(characteristic);
        }

        @Override
        public String getErrorMessage() {
            return context.getResources().getString(R.string.bt_error_reading) + " " + characteristic.getUuid().toString() + ".";
        }
    } // end of class ReadCommand


    protected class DiscoverCommand extends BluetoothCommand {

        public DiscoverCommand(BluetoothGatt gatt) {
            super(gatt);
        }

        @Override
        public boolean execute() {
            return gatt.discoverServices();
        }

        @Override
        public String getErrorMessage() {
            return context.getResources().getString(R.string.bt_error_discovering);
        }
    } // end of class DiscoverCommand


    // general class that holds data to a characteristic
    public static abstract class CharacteristicData {
        public UUID uuid;

        // will be called once the connection is established
        public abstract void process(Bluetooth b) throws BluetoothException;
    } // end of class CharacteristicData


    // characteristics for BluetoothInput
    public static class InputData extends CharacteristicData {
        public boolean extraTime;
        public int index;
        public Method conversionFunction;

        public InputData(UUID uuid, boolean extraTime, int index, Method conversionFunction) {
            this.uuid = uuid;
            this.extraTime = extraTime;
            this.index = index;
            if (!extraTime) {
                this.conversionFunction = conversionFunction;
            }
        }

        // finds the BluetoothGattCharacteristic and saves it in saveTime or mapping
        public void process(Bluetooth b) throws BluetoothException {
            BluetoothGattCharacteristic c = b.findCharacteristic(this.uuid);
            if (this.extraTime) {
                b.saveTime.put(c, this.index);
            } else {
                if (!b.mapping.containsKey(c)) {
                    // add Characteristic to the list for its BluetoothGattCharacteristic
                    b.mapping.put(c, new ArrayList<Characteristic>());
                }
                Characteristic toAdd = new Characteristic(this.index, this.conversionFunction);
                b.valuesSize++;
                b.mapping.get(c).add(toAdd);
            }
        }
    } // end of class InputData


    // Characteristics for BluetoothOutput
    public static class OutputData extends CharacteristicData {
        public int index;
        public Method conversionFunction;

        public OutputData(UUID uuid, int index, Method conversionFunction) {
            this.uuid = uuid;
            this.index = index;
            this.conversionFunction = conversionFunction;
        }

        // finds the BluetoothGattCharacteristic and saves it in mapping
        public void process(Bluetooth b) throws BluetoothException {
            BluetoothGattCharacteristic c = b.findCharacteristic(this.uuid);
            if (!b.mapping.containsKey(c)) {
                // add Characteristic to the list for its BluetoothGattCharacteristic
                b.mapping.put(c, new ArrayList<Characteristic>());
            }
            Characteristic toAdd = new Characteristic(this.index, this.conversionFunction);
            b.valuesSize++;
            b.mapping.get(c).add(toAdd);
        }
    } // end of class OutputData


    // Characteristics for Configuration
    public static class ConfigData extends CharacteristicData {
        public byte[] value;
        public Context context;

        public ConfigData(UUID uuid, String data, Method conversionFunction, Context context) throws phyphoxFile.phyphoxFileException {
            this.uuid = uuid;
            this.context = context;
            try {
                this.value = (byte[]) conversionFunction.invoke(null, data);
            } catch (Exception e) { // catch any exception that occurs in the conversion function
                throw new phyphoxFile.phyphoxFileException(context.getResources().getString(R.string.bt_exception_conversion3) + " \"" + conversionFunction.getName() + "\". ");
            }
        }

        // writes the configuration to the characteristic
        public void process(Bluetooth b) throws BluetoothException {
            BluetoothGattCharacteristic c = b.findCharacteristic(this.uuid);
            boolean result = c.setValue(this.value);
            if (!result) {
                throw new BluetoothException(context.getResources().getString(R.string.bt_exception_config) + " \"" + c.getUuid().toString() + "\" " + context.getResources().getString(R.string.bt_exception_config2), b);
            }
            b.cdl = new CancellableLatch(1);
            b.add(b.new WriteCommand(b.btGatt, c));
            result = false;
            try {
                // it should not be possible to continue before the notifications are turned on
                // timeout after 2 seconds if the device could not be connected
                result = b.cdl.await(2, TimeUnit.SECONDS); // short timeout to not let the user wait too long
            } catch (InterruptedException e) {
            }
            if (!result) {
                throw new BluetoothException(context.getResources().getString(R.string.bt_fail_writing), b);
            }
        }
    } // end of class ConfigData


    public static class BluetoothException extends Exception {
        Context context;
        String deviceAddress;
        String deviceName;

        public BluetoothException(String message, Bluetooth b) {
            super(message);
            // save necessary data for a more detailed error message
            this.context = b.context;
            this.deviceAddress = b.deviceAddress;
            this.deviceName = b.deviceName;
        }

        @Override
        // returns the message and adds data about the device so the user knows where the error happened.
        public String getMessage() {
            String message = super.getMessage();
            message += System.getProperty("line.separator") + context.getResources().getString(R.string.bt_exception_device);
            if (deviceAddress != null) {
                message += " " + context.getResources().getString(R.string.bt_exception_device_address) + " \"" + deviceAddress + "\"";
            }
            if (deviceName != null) {
                message += " " + context.getResources().getString(R.string.bt_exception_device_name) + " \"" + deviceName + "\"";
            }
            message += ".";
            return message;
        }
    } // end of class BluetoothException


    protected static class OnExceptionRunnable implements Runnable {
        public String message;
        public ProgressDialog progress;
        public Context context;
        public Runnable tryAgain;
        public Runnable cancel;
        AlertDialog alertDialog;

        @Override
        public void run() {
            if (context != null) {
                if (message.equals("")) {
                    message = context.getResources().getString(R.string.bt_default_error_message); // set default error message
                }
                ContextThemeWrapper ctw = new ContextThemeWrapper(context, R.style.phyphox);
                AlertDialog.Builder errorDialog = new AlertDialog.Builder(ctw);
                LayoutInflater neInflater = (LayoutInflater) ctw.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View neLayout = neInflater.inflate(R.layout.error_dialog, null);
                errorDialog.setView(neLayout);
                TextView tv = (TextView) neLayout.findViewById(R.id.errorText);
                tv.setText(message); // set error message as text
                if (tryAgain != null) {
                    errorDialog.setPositiveButton(context.getResources().getString(R.string.tryagain), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            tryAgain.run();
                        }
                    });
                }
                errorDialog.setNegativeButton(context.getResources().getString(R.string.cancel), new Dialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (progress != null) {
                            progress.dismiss();
                        }
                        if (cancel != null) {
                            cancel.run();
                        }
                    }
                });
                alertDialog = errorDialog.create();
                alertDialog.show();
            }
        }

        // dismisses dialogs
        public void dismiss() {
            if (alertDialog != null) {
                alertDialog.dismiss();
            }
            if (progress != null) {
                progress.dismiss();
            }
        }
    } // end of class OnExceptionRunnable


    // AsyncTask to connect to all  Bluetooth Devices.
    // In case of connection errors it shows a dialog with the message and the option to try again
    public static class ConnectBluetoothTask extends AsyncTask<Vector<? extends Bluetooth>, Void, String> {
        public Context context; // needs to be set if the error message should be shown
        public ProgressDialog progress; // will be dismissed when the task is done
        public Runnable onSuccess; // runs when the all Bluetooth devices are connected successfully

        @Override
        protected String doInBackground(final Vector<? extends Bluetooth>... params) {
            errorDialog.context = context;
            errorDialog.progress = progress;
            errorDialog.tryAgain = new Runnable() {
                @Override
                public void run() {
                    // start a new task with the same attributes
                    ConnectBluetoothTask btTask = new ConnectBluetoothTask();
                    btTask.progress = progress;
                    btTask.context = context;
                    btTask.onSuccess = onSuccess;
                    // show ProgressDialog again
                    if (progress != null) {
                        progress.show();
                    }
                    btTask.execute(params);
                }
            };
            // try to connect all devices
            for (Vector<? extends Bluetooth> v : params) {
                for (Bluetooth b : v) {
                    b.clear(); // clear command queue because there could be remains from the previous attempt
                    try {
                        b.connect();
                    } catch (Bluetooth.BluetoothException e) {
                        b.displayErrorMessage(e.getMessage(), true);
                        return e.getMessage();
                    }
                }
            }
            return null; // no error message
        }

        @Override
        protected void onPostExecute(String result) {
            if (progress != null) {
                progress.hide(); // don't dismiss progress yet because maybe "try again" will be called
            }
            if (result == null) {
                progress.dismiss();
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }
        }

        @Override
        protected void onCancelled() {
            if (progress != null) {
                progress.dismiss();
            }
        }
    } // end of class ConnectBluetoothTask


    // AsyncTask to reconnect the bluetooth device.
    // if it fails, the error will be displayed and a new task will be started as long as the experiment is running
    private class ReconnectBluetoothTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            // try to connect all devices
            try {
                clear(); // clear command queue because there could be remains from the previous attempt
                connect();
            } catch (Bluetooth.BluetoothException e) {
                return e.getMessage();
            }
            return null; // no error message
        }

        @Override
        protected void onPostExecute(String result) {
            // show the error dialog if the context is set
            if (isRunning && result != null) {
                displayErrorMessage(result);
                (new ReconnectBluetoothTask()).execute(); // try again
            } else {
                toast.cancel(); // cancel error messages if the experiment is not running anymore or if reconnecting was successful
            }
        }

        @Override
        protected void onCancelled() {
            toast.cancel();
        }
    } // end of class ReconnectBluetoothTask

}