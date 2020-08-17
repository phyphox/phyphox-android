package de.rwth_aachen.phyphox.Bluetooth;

import android.app.Activity;
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
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.PhyphoxFile;

/**
 * The Bluetooth class encapsulates a generic Bluetooth connection and deals with the following tasks:
 * <ul>
 * <li>connecting a device,</li>
 * <li>operating on characteristics,</li>
 * <li>queueing the commands on the BluetoothGatt object,</li>
 * <li>giving opportunities to display error messages,</li>
 * <li>closing the connection.</li>
 * </ul>
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class Bluetooth implements Serializable {

    public final static UUID baseUUID = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb");
    public final static UUID phyphoxServiceUUID = UUID.fromString("cddf0001-30f7-4671-8b43-5e40ba53514a");
    public final static UUID phyphoxExperimentCharacteristicUUID = UUID.fromString("cddf0002-30f7-4671-8b43-5e40ba53514a");
    public final static UUID phyphoxExperimentControlCharacteristicUUID = UUID.fromString("cddf0003-30f7-4671-8b43-5e40ba53514a");

    transient private static BluetoothAdapter btAdapter;
    public static OnExceptionRunnable errorDialog = new OnExceptionRunnable();

    protected transient BluetoothDevice btDevice;
    protected transient BluetoothGatt btGatt;
    public String idString;
    public String deviceName;
    public String deviceAddress;
    public UUID uuidFilter;
    public Boolean autoConnect;
    public int requestMTU;

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
     * each BluetoothGattCharacteristic that should be read or written maps with an array list of Characteristics
     */
    protected HashMap<BluetoothGattCharacteristic, ArrayList<Characteristic>> mapping = new HashMap<>();
    /**
     * used for important asynchronous tasks for example connectGatt
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
     * queue for the BluetoothCommands
     */
    private LinkedList<BluetoothCommand> commandQueue;
    /**
     * indicates whether a command is being executed at the moment
     */
    private Boolean isExecuting;

    protected Activity activity;
    protected Context context;
    /**
     * used to display errors while the experiment is running
     */
    protected Toast toast;
    /**
     * is able to change UI and is used for example to display errors
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
     * @param idString        An identifier given by the experiment author used to group multiple devices and allow the user to distinguish them
     * @param deviceName      name of the device (can be null if deviceAddress is not null)
     * @param deviceAddress   address of the device (can be null if deviceName is not null)
     * @param uuidFilter      Optional filter to identify a device by an advertised service or characteristic
     * @param autoConnect     If true, phyphox will not show a scan dialog and connect with the first matching device instead
     * @param context         context
     * @param characteristics list of all characteristics the object should be able to operate on
     */
    public Bluetooth(String idString, String deviceName, String deviceAddress, UUID uuidFilter, Boolean autoConnect, Activity activity, Context context, Vector<CharacteristicData> characteristics) {
        this.idString = idString;
        this.deviceName = (deviceName == null ? "" : deviceName);
        this.deviceAddress = deviceAddress;
        this.uuidFilter = uuidFilter;
        this.autoConnect = autoConnect;

        this.activity = activity;
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
     * Connect with the device.
     *
     * @throws BluetoothException if there is an error on findDevice, openConnection or process CharacteristicData
     */
    public void connect(Map<String, BluetoothDevice> knownDevices) throws BluetoothException {
        if (btDevice == null) {
            findDevice(knownDevices);
        }
        if (btDevice == null)
            return;
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
     * Search for the device with the specified name or address on the list of paired devices.
     *
     * @throws BluetoothException if Bluetooth is disabled or if the device could not be found
     */
    public void findDevice(Map<String,BluetoothDevice> knownDevices) throws BluetoothException {
        if (!isEnabled()) {
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_disabled), this);
        }

        //First check if we have already connected to a device with the same idString
        if (idString != null && !idString.isEmpty() && knownDevices != null && knownDevices.containsKey(idString)) {
            btDevice = knownDevices.get(idString);
            return;
        }

        // First check paired devices - those get precedence
        for (BluetoothDevice d : getPairedDevices()) {
            if (!deviceName.isEmpty() && (deviceAddress == null || deviceAddress.isEmpty())) {
                if (d.getName().contains(deviceName)) {
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
        if (btDevice == null && deviceAddress != null && !deviceAddress.isEmpty()) {
            btDevice = btAdapter.getRemoteDevice(deviceAddress);
        }
        if (btDevice == null) {
            //No matching device found - Now we have to scan for unpaired devices and present possible matches to the user if there are more than one.

            BluetoothScanDialog bsd = new BluetoothScanDialog(autoConnect, activity, context, btAdapter);
            if (!bsd.scanPermission())
                return;
            if (!bsd.locationEnabled())
                return;
            BluetoothScanDialog.BluetoothDeviceInfo bdi = bsd.getBluetoothDevice(deviceName, uuidFilter, null, null, idString);
            if (bdi != null)
                btDevice = bdi.device;
        }
        if (btDevice == null) {
            //still null? Give up and complain
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_notfound), this);
        }
    }

    /**
     * Connect to the Gatt Server hosted by the specified device and discover its Services.
     * <p>
     * The calls to connectGatt and discoverServices both have a lock because it is not possible to continue before they succeed.
     * The timeout of the lock is set to 5 seconds.
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
        btGatt = btDevice.connectGatt(context, false, btLeGattCallback);
        try {
            // it should not be possible to continue before the device is connected
            // timeout after 5 seconds if the device could not be connected
            result = cdl.await(10, TimeUnit.SECONDS);
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
            result = cdl.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
        if (!result) {
            throw new BluetoothException(context.getResources().getString(R.string.bt_exception_services), this);
        }
    }

    /**
     * Close the connection to the Gatt server of the device.
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
     * @param data           data read from the characteristic
     * @param characteristic characteristic that got the notification
     */
    protected void retrieveData(byte[] data, BluetoothGattCharacteristic characteristic) {
    }

    /**
     * Called when a BluetoothGattCharacteristic was read.
     *
     * @param data           data read from the characteristic
     * @param characteristic characteristic that was read
     */
    protected void saveData(byte[] data, BluetoothGattCharacteristic characteristic) {
    }

    /**
     * Searches the device for the specified BluetoothGattCharacteristic.
     *
     * @param uuid UUID of the BluetoothGattCharacteristic
     * @return the BluetoothGattCharacteristic
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
     *Display the error message (as Toast if the experiment is running, as AlertDialog if not).
     *
     * @param message will be displayed
     */
    protected void displayErrorMessage(final String message) {
        displayErrorMessage(message, !isRunning);
    }

    /**
     * Display the error message as Toast or as AlertDialog.
     *
     * @param message    will be displayed
     * @param showDialog display message in an AlertDialog if true and in a Toast if false
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
     * Execute the next command in the queue if there is no command running and the queue is not empty.
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
     * Add a new command to the commandQueue and make sure that it will be executed.
     * There is a limit of commands in the queue and the command will only be added if the limit is not reached yet.
     * <p>
     * The limit is set to the maximum of 10 or twice the number of BluetoothGattCharacteristics in mapping.
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
     * Clear the command queue.
     */
    protected void clear() {
        commandQueue.clear();
    }


    /**
     * Callback for Gatt operations.
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
                displayErrorMessage(context.getResources().getString(R.string.bt_fail_reading) + BluetoothException.getMessage(Bluetooth.this));
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
                    displayErrorMessage(context.getResources().getString(R.string.bt_fail_writing) + BluetoothException.getMessage(Bluetooth.this));
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
                        if (requestMTU > 0) {
                            add(new RequestMTUCommand(gatt, requestMTU));
                        }
                        if (isRunning) {
                            try {
                                start(); // start collecting / sending data again if the experiment is running
                            } catch (BluetoothException e) {
                                // means that the device disconnected so error handling will come anyway
                            }
                        }
                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    // fall through to default
                default:
                    if (isRunning) {
                        stop(); // stop collecting data
                        forcedBreak = true;
                        isRunning = true; // the experiment is still running, only bluetooth has stopped
                        displayErrorMessage(context.getResources().getString(R.string.bt_exception_disconnected) + BluetoothException.getMessage(Bluetooth.this));
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


    /**
     * CountDownLatch that can be cancelled.
     * Used for important gatt-commands where it should not be possible to continue before the command succeeds.
     */
    protected static class CancellableLatch extends CountDownLatch {
        /**
         * Indicates whether the Latch was counted down or if it was cancelled
         */
        private boolean cancelled;

        /**
         * Create a new CancellableLatch.
         *
         * @param count number of times the latch has to be counted down.
         */
        public CancellableLatch(int count) {
            super(count);
            cancelled = false;
        }

        /**
         * Returns true if the latch was counted down (and not cancelled) before the timeout.
         *
         * @param timeout the maximum time to wait
         * @param unit    the time unit of the timeout argument
         * @return true if the latch was counted down before the timeout without being cancelled
         * @throws InterruptedException if the current thread is interrupted while waiting
         */
        @Override
        public boolean await(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
            boolean result = super.await(timeout, unit);
            return !cancelled && result;
        }

        /**
         * Counts down the latch to let the thread continue but await() will return false.
         */
        public void cancel() {
            cancelled = true;
            while (getCount() > 0) {
                countDown();
            }
        }
    } // end of class CancellableLatch


    /**
     * Represents the attributes of a Characteristic as they are defined in the phyphox-File.
     */
    protected static class Characteristic {
        /**
         * Index of the buffer the characteristic value should be saved in.
         */
        public int index;
        /**
         * Method that will be called to convert the value of the characteristic.
         */
        public ConversionsConfig.ConfigConversion configConversionFunction = null;
        public ConversionsInput.InputConversion inputConversionFunction = null;
        public ConversionsOutput.OutputConversion outputConversionFunction = null;

        /**
         * Create a new Characteristic.
         *
         * @param index              Index of the buffer the characteristic value should be saved in
         * @param conversionFunction Method that will be called to convert the value of the characteristic
         */
        public Characteristic(int index, ConversionsConfig.ConfigConversion conversionFunction) {
            this.index = index;
            this.configConversionFunction = conversionFunction;
        }

        public Characteristic(int index, ConversionsInput.InputConversion conversionFunction) {
            this.index = index;
            this.inputConversionFunction = conversionFunction;
        }

        public Characteristic(int index, ConversionsOutput.OutputConversion conversionFunction) {
            this.index = index;
            this.outputConversionFunction = conversionFunction;
        }
    } // end of class Characteristic


    /**
     * Skeletal implementation of BluetoothCommands that can be queued.
     */
    protected abstract class BluetoothCommand {
        /**
         * BluetoothGatt the command should be executed on.
         */
        BluetoothGatt gatt;
        /**
         * BluetoothGattCharacteristic the command should be executed on.
         */
        BluetoothGattCharacteristic characteristic;

        /**
         * Create a new BluetoothCommand.
         *
         * @param gatt           BluetoothGatt the command should be executed on
         * @param characteristic BluetoothGattCharacteristic the command should be executed on
         */
        public BluetoothCommand(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            this.gatt = gatt;
            this.characteristic = characteristic;
        }

        /**
         * Create a new BluetoothCommand that does not refer to a BluetoothGattCharacteristic.
         *
         * @param gatt BluetoothGatt the command should be executed on
         */
        public BluetoothCommand(BluetoothGatt gatt) {
            this.gatt = gatt;
            this.characteristic = null;
        }

        /**
         * Execute the BluetoothCommand.
         *
         * @return true if the operation was initiated successfully
         */
        public abstract boolean execute();

        /**
         * Return the error message that should be displayed if execute() returns false
         *
         * @return an error message
         */
        public abstract String getErrorMessage();
    } // end of class BluetoothCommand


    /**
     * Command to write a BluetoothGattCharacteristic that can be queued.
     */
    protected class WriteCommand extends BluetoothCommand {

        /**
         * Create a new WriteCommand.
         *
         * @param gatt           BluetoothGatt the command should be executed on
         * @param characteristic BluetoothGattCharacteristic that should be written
         */
        public WriteCommand(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super(gatt, characteristic);
        }

        /**
         * Write the BluetoothGattCharacteristic with WRITE_TYPE_NO_RESPONSE.
         *
         * @return true if the operation was initiated successfully.
         */
        @Override
        public boolean execute() {
            //No response does not work with the BBC Micro Bit. Need to investigate further...
            //characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            return gatt.writeCharacteristic(characteristic);
        }

        /**
         * Return the error message that should be displayed if execute() returns false
         *
         * @return the error message
         */
        @Override
        public String getErrorMessage() {
            return context.getResources().getString(R.string.bt_error_writing) + " " + characteristic.getUuid().toString() + ".";
        }
    } // end of class WriteCommand


    /**
     * Command to write a BluetoothGattDescriptor that can be queued.
     */
    protected class WriteDescriptorCommand extends BluetoothCommand {
        /**
         * BluetoothGattDescriptor that should be written.
         */
        private BluetoothGattDescriptor descriptor;

        /**
         * Create a new WriteDescriptorCommand.
         *
         * @param gatt       BluetoothGatt the command should be executed on
         * @param descriptor BluetoothGattDescriptor that should be written
         */
        public WriteDescriptorCommand(BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
            super(gatt);
            this.descriptor = descriptor;
        }

        /**
         * Write the BluetoothGattDescriptor.
         *
         * @return true if the operation was initiated successfully.
         */
        @Override
        public boolean execute() {
            return gatt.writeDescriptor(descriptor);
        }

        /**
         * Return the error message that should be displayed if execute() returns false
         *
         * @return the error message
         */
        @Override
        public String getErrorMessage() {
            return ""; // error message will not be displayed
        }
    } // end of class WriteDescriptorCommand


    /**
     * Command to read a BluetoothGattCharacteristic that can be queued.
     */
    protected class ReadCommand extends BluetoothCommand {

        /**
         * Create a new WriteCommand.
         *
         * @param gatt           BluetoothGatt the command should be executed on
         * @param characteristic BluetoothGattCharacteristic that should be read
         */
        public ReadCommand(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super(gatt, characteristic);
        }

        /**
         * Read the BluetoothGattCharacteristic.
         *
         * @return true if the operation was initiated successfully.
         */
        @Override
        public boolean execute() {
            return gatt.readCharacteristic(characteristic);
        }

        /**
         * Return the error message that should be displayed if execute() returns false
         *
         * @return the error message
         */
        @Override
        public String getErrorMessage() {
            return context.getResources().getString(R.string.bt_error_reading) + " " + characteristic.getUuid().toString() + ".";
        }
    } // end of class ReadCommand


    /**
     * Command to discover services of a BluetoothGatt that can be queued.
     */
    protected class DiscoverCommand extends BluetoothCommand {

        /**
         * Create a new DiscoverCommand.
         *
         * @param gatt BluetoothGatt whose services should be discovered
         */
        public DiscoverCommand(BluetoothGatt gatt) {
            super(gatt);
        }

        /**
         * Discover services.
         *
         * @return true if the operation was initiated successfully.
         */
        @Override
        public boolean execute() {
            return gatt.discoverServices();
        }

        /**
         * Return the error message that should be displayed if execute() returns false
         *
         * @return the error message
         */
        @Override
        public String getErrorMessage() {
            return context.getResources().getString(R.string.bt_error_discovering);
        }
    } // end of class DiscoverCommand

    /**
     * Command to request a specific MTU size
     */
    protected class RequestMTUCommand extends BluetoothCommand {
        int mtu;
        /**
         * Create a new RequestMTUCommand.
         *
         * @param gatt BluetoothGatt whose services should be discovered
         */
        public RequestMTUCommand(BluetoothGatt gatt, int mtu) {
            super(gatt);
            this.mtu = mtu;
        }

        /**
         * Discover services.
         *
         * @return true if the operation was initiated successfully.
         */
        @Override
        public boolean execute() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return gatt.requestMtu(mtu);
            } else
                return false;
        }

        /**
         * Return the error message that should be displayed if execute() returns false
         *
         * @return the error message
         */
        @Override
        public String getErrorMessage() {
            return "Could not set MTU as requested by the experiment configuration.";
        }
    } // end of class RequestMTUCommand


    /**
     * Holds data to a Characteristic that was collected from phyphoxFile.
     */
    public static abstract class CharacteristicData {
        /**
         * UUID of the Characteristic.
         */
        public UUID uuid;

        /**
         * Called once the connection is established to add the Characteristic to the Bluetooth object.
         *
         * @param b Bluetooth to find the BluetoothGattCharacteristic
         * @throws BluetoothException
         */
        public abstract void process(Bluetooth b) throws BluetoothException;
    } // end of class CharacteristicData


    /**
     * Holds data to a Characteristic for BluetoothInput that was collected from phyphoxFile.
     */
    public static class InputData extends CharacteristicData {
        /**
         * True if the Characteristic has extra=time
         */
        public boolean extraTime;
        /**
         * Index of the buffer
         */
        public int index;
        /**
         * Method that will be called to convert the value of the characteristic
         */
        public ConversionsInput.InputConversion conversionFunction;

        /**
         * Create a new InputData.
         *
         * @param uuid               UUID of the Characteristic
         * @param extraTime          true if the time and not the value should be saved
         * @param index              index of the buffer
         * @param conversionFunction InputConversion instance that will used to convert the value of the characteristic
         */
        public InputData(UUID uuid, boolean extraTime, int index, ConversionsInput.InputConversion conversionFunction) {
            this.uuid = uuid;
            this.extraTime = extraTime;
            this.index = index;
            if (!extraTime) {
                this.conversionFunction = conversionFunction;
            }
        }

        /**
         * Find the BluetoothGattCharacteristic and add the Characteristic to saveTime or mapping.
         *
         * @param b Bluetooth to find the BluetoothGattCharacteristic
         * @throws BluetoothException if the BluetoothGattCharacteristic could not be found
         */
        @Override
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


    /**
     * Holds data to a Characteristic for BluetoothOutput that was collected from phyphoxFile.
     */
    public static class OutputData extends CharacteristicData {
        /**
         * Index of the buffer.
         */
        public int index;
        /**
         * Method that will be called to convert the value of the characteristic.
         */
        public ConversionsOutput.OutputConversion conversionFunction;

        /**
         * Create a new OutputData.
         *
         * @param uuid               UUID of the Characteristic
         * @param index              index of the buffer
         * @param conversionFunction OutputConversion instance that will be used to convert the value of the characteristic
         */
        public OutputData(UUID uuid, int index, ConversionsOutput.OutputConversion conversionFunction) {
            this.uuid = uuid;
            this.index = index;
            this.conversionFunction = conversionFunction;
        }

        /**
         * Find the BluetoothGattCharacteristic and add the Characteristic to mapping.
         *
         * @param b Bluetooth to find the BluetoothGattCharacteristic.
         * @throws BluetoothException if the BluetoothGattCharacteristic could not be found
         */
        @Override
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


    /**
     * Holds data to a Characteristic that should be configured that was collected from phyphoxFile.
     */
    public static class ConfigData extends CharacteristicData {
        /**
         * Data that should be written to the characteristic.
         */
        public byte[] value;

        /**
         * Create a new ConfigData.
         *
         * @param uuid               UUID of the characteristic
         * @param data               data that will be converted to the value that should be written
         * @param conversionFunction ConfigConversion instance that will be used to convert the value of the characteristic
         * @throws PhyphoxFile.phyphoxFileException if there is an error while converting the data
         */
        public ConfigData(UUID uuid, String data, ConversionsConfig.ConfigConversion conversionFunction) throws PhyphoxFile.phyphoxFileException {
            this.uuid = uuid;
            try {
                this.value = conversionFunction.convert(data);
            } catch (Exception e) { // catch any exception that occurs in the conversion function
                throw new PhyphoxFile.phyphoxFileException("An error occurred on the conversion function" + " \"" + conversionFunction.getClass().getName() + "\". ");
            }
        }

        /**
         * Writes the configuration to the specified Characteristic.
         * The call to writeCharacteristic has a lock because it should not be possible to continue before it succeeds.
         * <p>
         * The timeout of the lock is set to 2 seconds.
         *
         * @param b Bluetooth to write the Characteristic
         * @throws BluetoothException if the value could not be set or written
         */
        @Override
        public void process(Bluetooth b) throws BluetoothException {
            BluetoothGattCharacteristic c = b.findCharacteristic(this.uuid);
            if (c.getValue() != null && c.getValue().equals(this.value)) {
                return; // value is already set
            }
            boolean result = c.setValue(this.value);
            if (!result) {
                throw new BluetoothException(b.context.getResources().getString(R.string.bt_exception_config) + " \"" + c.getUuid().toString() + "\" " + b.context.getResources().getString(R.string.bt_exception_config2), b);
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
                throw new BluetoothException(b.context.getResources().getString(R.string.bt_fail_writing), b);
            }
        }
    } // end of class ConfigData


    /**
     * Thrown to indicate that there was an error concerning Bluetooth.
     */
    public static class BluetoothException extends Exception {

        /**
         * Create new BluetoothException and add device data to the message.
         *
         * @param message the detail message
         * @param b       Bluetooth to add device data
         */
        public BluetoothException(String message, Bluetooth b) {
            super(message + getMessage(b));
        }

        /**
         * Return a String with the device data (address and name if not null).
         *
         * @param b Bluetooth
         * @return String representation about the device data
         */
        public static String getMessage(Bluetooth b) {
            String message = System.getProperty("line.separator") + b.context.getResources().getString(R.string.bt_exception_device);
            if (b.deviceAddress != null) {
                message += " " + b.context.getResources().getString(R.string.bt_exception_device_address) + " \"" + b.deviceAddress + "\"";
            }
            if (b.deviceName != null) {
                message += " " + b.context.getResources().getString(R.string.bt_exception_device_name) + " \"" + b.deviceName + "\"";
            }
            message += ".";
            return message;
        }
    } // end of class BluetoothException


    /**
     * Runnable that displays an AlertDialog with an error message and has the option to try again.
     * The attributes are public so they can be set.
     */
    public static class OnExceptionRunnable implements Runnable {
        /**
         * Message that will be displayed.
         */
        public String message;
        /**
         * Application context.
         */
        public Context context;
        /**
         * Run on try again.
         */
        public Runnable tryAgain;
        /**
         * Run on cancel.
         */
        public Runnable cancel;

        /**
         * Create the AlertDialog and show it.
         */
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
                        if (cancel != null) {
                            cancel.run();
                        }
                    }
                });
                AlertDialog alertDialog = errorDialog.create();
                alertDialog.show();
            }
        }
    } // end of class OnExceptionRunnable

    public static Map<String, BluetoothDevice> knownDevicesFromIO(final Vector<? extends Bluetooth>... list) {
        Map<String, BluetoothDevice> knownDevices = new HashMap<>();
        for (Vector<? extends Bluetooth> v : list) {
            for (Bluetooth b : v) {
                if (b.btDevice != null && b.idString != null && !b.idString.isEmpty())
                    knownDevices.put(b.idString, b.btDevice);
            }
        }
        return knownDevices;
    }

    /**
     * AsyncTask to connect all Bluetooth devices.
     * In case of connection errors it shows a dialog with the message and the option to start the task again.
     */
    public static class ConnectBluetoothTask extends AsyncTask<Vector<? extends Bluetooth>, Void, String> {
        /**
         * ProgressDialog to show while the devices are being connected.
         */
        public ProgressDialog progress;
        /**
         * Will be run when all Bluetooth devices are connected successfully.
         */
        public Runnable onSuccess;

        /**
         * Try to connect all Bluetooth devices and display the error if there is one.
         *
         * @param params Vector of Bluetooth that should be connected
         * @return error message or null if there was no error
         */
        @Override
        protected String doInBackground(final Vector<? extends Bluetooth>... params) {
            // try to connect all devices
            for (Vector<? extends Bluetooth> v : params) {
                for (Bluetooth b : v) {
                    b.clear(); // clear command queue because there could be remains from the previous attempt
                    try {
                        b.connect(knownDevicesFromIO(params));
                    } catch (Bluetooth.BluetoothException e) {
                        b.displayErrorMessage(e.getMessage(), true);
                        return e.getMessage();
                    }
                }
            }
            return null; // no error message
        }

        /**
         * Hide progress and - if there was no error - dismiss progress and run onSuccess.
         *
         * @param result error message or null if there was no error
         */
        @Override
        protected void onPostExecute(String result) {
            if (progress != null) {
                progress.hide(); // don't dismiss progress yet because maybe "try again" will be called
            }
            if (result == null) {
                if (progress != null) {
                    progress.dismiss();
                }
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }
        }

        /**
         * Dismiss ProgressDialog.
         */
        @Override
        protected void onCancelled() {
            if (progress != null) {
                progress.dismiss();
            }
            super.onCancelled();
        }
    } // end of class ConnectBluetoothTask


    /**
     * AsyncTask to reconnect the Bluetooth device.
     * If it fails, the error will be displayed (in a Toast because the experiment is running) and a new task will be started as long as the experiment is running.
     */
    private class ReconnectBluetoothTask extends AsyncTask<Void, Void, String> {

        /**
         * Connect Bluetooth device.
         *
         * @param params void
         * @return error message or null if there was no error
         */
        @Override
        protected String doInBackground(Void... params) {
            try {
                clear(); // clear command queue because there could be remains from the previous attempt
                connect(null);
            } catch (Bluetooth.BluetoothException e) {
                return e.getMessage();
            }
            return null; // no error message
        }

        /**
         * Display error message and start a new task if there was an error and the experiment is still running
         * or cancel the toast if there was no error.
         *
         * @param result error message or null if there was no error
         */
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

        /**
         * Cancel toast.
         */
        @Override
        protected void onCancelled() {
            toast.cancel();
            super.onCancelled();
        }
    } // end of class ReconnectBluetoothTask

} // end of class Bluetooth
