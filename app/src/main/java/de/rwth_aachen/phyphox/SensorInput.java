package de.rwth_aachen.phyphox;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.Serializable;
import java.security.InvalidParameterException;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

//The sensorInput class encapsulates a sensor, maps their name from the phyphox-file format to
//  the android identifiers and handles their output, which is written to the dataBuffers
public class SensorInput implements SensorEventListener, Serializable {

    public int type; //Sensor type (Android identifier)
    public SensorName sensorName; //Sensor name (phyphox identifier)
    public boolean calibrated = true;
    public long period; //Sensor aquisition period in nanoseconds (inverse rate), 0 corresponds to as fast as possible
    public int stride;
    int strideCount;
    public SensorRateStrategy rateStrategy;
    private boolean lastOneTooFast = false;
    private final ExperimentTimeReference experimentTimeReference; //the start time of the measurement. This allows for timestamps relative to the beginning of a measurement
    public double fixDeviceTimeOffset = 0.0; //Some devices show a negative offset on the time events of the sensors. This seems to mostly affect sensors that report onChange. Our strategy is that sensor events with -10s < t < 0s are simply corrected to t=0 (old sensor events in the queue are still valid), but sensor events with t < -10s indicate a systematic problem on the device and are used to calculate this variable which will be used to also correct all subsequent readings.

    public boolean ignoreUnavailable = false;

    public DataBuffer dataX; //Data-buffer for x
    public DataBuffer dataY; //Data-buffer for y (3D sensors only)
    public DataBuffer dataZ; //Data-buffer for z (3D sensors only)
    public DataBuffer dataT; //Data-buffer for t
    public DataBuffer dataAbs; //Data-buffer for absolute value
    public DataBuffer dataAccuracy; //Data-buffer for absolute value
    transient private SensorManager sensorManager; //Hold the sensor manager

    private long lastReading; //Remember the time of the last reading to fullfill the rate
    private double avgX, avgY, avgZ, avgAccuracy; //Used for averaging
    private double genX, genY, genZ, genAccuracy; //Store last output for "generate" rate strategy
    private boolean average = false; //Avergae over aquisition period?
    private int aquisitions; //Number of aquisitions for this average

    private Lock dataLock;

    public boolean vendorSensor = false;
    public Sensor sensor;

    public enum SensorName {
        accelerometer, linear_acceleration, gravity, gyroscope, magnetic_field, pressure, light, proximity, temperature, humidity, attitude
    }

    public enum SensorRateStrategy {
        auto, request, generate, limit
    }

    public class SensorException extends Exception {
        public SensorException(String message) {
            super(message);
        }
    }

    public static int resolveSensorName(SensorName type) {
        //Interpret the type string
        switch (type) {
            case accelerometer: return Sensor.TYPE_ACCELEROMETER;
            case linear_acceleration: return Sensor.TYPE_LINEAR_ACCELERATION;
            case gravity: return Sensor.TYPE_GRAVITY;
            case gyroscope: return Sensor.TYPE_GYROSCOPE;
            case magnetic_field: return Sensor.TYPE_MAGNETIC_FIELD;
            case pressure: return Sensor.TYPE_PRESSURE;
            case light: return Sensor.TYPE_LIGHT;
            case proximity: return Sensor.TYPE_PROXIMITY;
            case temperature: return Sensor.TYPE_AMBIENT_TEMPERATURE;
            case humidity: return Sensor.TYPE_RELATIVE_HUMIDITY;
            case attitude: return Sensor.TYPE_ROTATION_VECTOR;
            default: return -1;
        }
    }

    public static int resolveSensorString(String type) {
        try {
            return resolveSensorName(SensorName.valueOf(type));
        } catch (InvalidParameterException e) {
            return -1;
        }
    }

    private SensorInput(boolean ignoreUnavailable, double rate, SensorRateStrategy rateStrategy, int stride, boolean average, Vector<DataOutput> buffers, Lock lock, ExperimentTimeReference experimentTimeReference) throws SensorException {
        this.dataLock = lock;
        this.experimentTimeReference = experimentTimeReference;

        if (rate <= 0)
            this.period = 0;
        else
            this.period = (long) ((1 / rate) * 1e9); //Period in ns
        this.stride = stride;
        this.rateStrategy = rateStrategy;

        this.average = average;

        this.ignoreUnavailable = ignoreUnavailable;

        //Store the buffer references if any
        if (buffers == null)
            return;

        buffers.setSize(6);
        if (buffers.get(0) != null)
            this.dataX = buffers.get(0).buffer;
        if (buffers.get(1) != null)
            this.dataY = buffers.get(1).buffer;
        if (buffers.get(2) != null)
            this.dataZ = buffers.get(2).buffer;
        if (buffers.get(3) != null)
            this.dataT = buffers.get(3).buffer;
        if (buffers.get(4) != null)
            this.dataAbs = buffers.get(4).buffer;
        if (buffers.get(5) != null)
            this.dataAccuracy = buffers.get(5).buffer;
    }

    //The constructor needs the phyphox identifier of the sensor type, the desired aquisition rate,
    // and the four buffers to receive x, y, z and t. The data buffers may be null to be left unused.
    public SensorInput(SensorName type, boolean ignoreUnavailable, double rate, SensorRateStrategy rateStrategy, int stride, boolean average, Vector<DataOutput> buffers, Lock lock, ExperimentTimeReference experimentTimeReference) throws SensorException {
        this(ignoreUnavailable, rate, rateStrategy, stride, average, buffers, lock, experimentTimeReference);

        this.type = resolveSensorName(type);
        this.sensorName = type;
        if (this.type < 0)
            throw new SensorException("Unknown sensor.");
    }

    public SensorInput(String type, boolean ignoreUnavailable, double rate, SensorRateStrategy rateStrategy, int stride, boolean average, Vector<DataOutput> buffers, Lock lock, ExperimentTimeReference experimentTimeReference) throws SensorException {
        this(ignoreUnavailable, rate, rateStrategy, stride, average, buffers, lock, experimentTimeReference);

        this.type = resolveSensorString(type);
        if (this.type < 0)
            throw new SensorException("Unknown sensor.");

        this.sensorName = SensorName.valueOf(type);
    }

    private Sensor findSensor() {
        Sensor sensor = sensorManager.getDefaultSensor(type);

        if (sensor != null) {
            vendorSensor = false;
            return sensor;
        } else {
            for (Sensor s : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
                if (s.getType() < Sensor.TYPE_DEVICE_PRIVATE_BASE)
                    continue;
                String name = s.getName().toLowerCase();
                if (type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                    if (name.toLowerCase().contains("temperature") || name.toLowerCase().contains("thermo")) { //Samsung devices have an entry "thermistor", but we do not get any data by naively reading it, so do not add it unless there are other devices that benefit from accepting this name.
                        sensor = s;
                        break;
                    }
                }
                if (type == Sensor.TYPE_RELATIVE_HUMIDITY) {
                    if (name.toLowerCase().contains("humidity")) {
                        sensor = s;
                        break;
                    }
                }
                if (type == Sensor.TYPE_PRESSURE) {
                    if (name.toLowerCase().contains("pressure")) {
                        sensor = s;
                        break;
                    }
                }
            }
        }

        if (sensor != null) {
            vendorSensor = true;
            return sensor;
        }

        return null;
    }

    public void attachSensorManager(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
        sensor = findSensor();
    }

    //Check if the sensor is available without trying to use it.
    public boolean isAvailable() {
        return (sensor != null);
    }

    //Get the internationalization string for a sensor type
    public static int getDescriptionRes(int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                return R.string.sensorAccelerometer;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return R.string.sensorLinearAcceleration;
            case Sensor.TYPE_GRAVITY:
                return R.string.sensorGravity;
            case Sensor.TYPE_GYROSCOPE:
                return R.string.sensorGyroscope;
            case Sensor.TYPE_MAGNETIC_FIELD:
                return R.string.sensorMagneticField;
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                return R.string.sensorMagneticField;
            case Sensor.TYPE_PRESSURE:
                return R.string.sensorPressure;
            case Sensor.TYPE_LIGHT:
                return R.string.sensorLight;
            case Sensor.TYPE_PROXIMITY:
                return R.string.sensorProximity;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return R.string.sensorTemperature;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                return R.string.sensorHumidity;
            case Sensor.TYPE_ROTATION_VECTOR:
                return R.string.sensorAttitude;
        }
        if (type >= Sensor.TYPE_DEVICE_PRIVATE_BASE) {
            return R.string.sensorVendor;
        }
        return R.string.unknown;
    }

    public int getDescriptionRes() {
        return SensorInput.getDescriptionRes(type);
    }

    public static String getUnit(int type) {
        switch (type) {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return "m/s²";
            case Sensor.TYPE_LIGHT:
                return "lx";
            case Sensor.TYPE_GYROSCOPE:
                return "rad/s";
            case Sensor.TYPE_ACCELEROMETER:
                return "m/s²";
            case Sensor.TYPE_MAGNETIC_FIELD:
                return "µT";
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                return "µT";
            case Sensor.TYPE_PRESSURE:
                return "hPa";
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return "°C";
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                return "%";
            case Sensor.TYPE_PROXIMITY:
                return "cm";
        }
        return "";
    }

    //Start the data aquisition by registering a listener for this sensor.
    public void start() {
        if (sensor == null)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && (type == Sensor.TYPE_MAGNETIC_FIELD || type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)) {
            if (calibrated)
                this.type = Sensor.TYPE_MAGNETIC_FIELD;
            else
                this.type = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED;
        }

        //Reset averaging
        this.lastReading = 0;
        this.avgX = 0.;
        this.avgY = 0.;
        this.avgZ = 0.;
        this.avgAccuracy = 0.;
        this.aquisitions = 0;
        this.strideCount = 0;
        lastOneTooFast = false;

        if (rateStrategy == SensorRateStrategy.request || rateStrategy == SensorRateStrategy.auto)
            this.sensorManager.registerListener(this, sensor, (int)(period / 1000));
        else
            this.sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    //Stop the data aquisition by unregistering the listener for this sensor
    public void stop() {
        if (sensor == null)
            return;
        this.sensorManager.unregisterListener(this);
    }

    //This event listener is mandatory as this class implements SensorEventListener
    //But phyphox does not need it
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void appendToBuffers(long timestamp, double x, double y, double z, double accuracy) {
        strideCount++;
        if (strideCount < stride) {
            return;
        } else {
            strideCount = 0;
        }
        dataLock.lock();
        try {
            if (dataX != null)
                dataX.append(x);
            if (dataY != null)
                dataY.append(y);
            if (dataZ != null)
                dataZ.append(z);
            if (dataT != null) {
                double t;
                if (timestamp == 0) {
                    //This is nonsense. The timestamp should be given in nanoseconds since system boot.
                    //Receiving exactly 0 is very unlikely to be an offset, but most likely to be a bad
                    //implementation on the device. We will do an best effort fix it by retrieving our
                    //own timestamp.
                    t = experimentTimeReference.getExperimentTime();
                } else {
                    t = experimentTimeReference.getExperimentTimeFromEvent(timestamp);
                    if ((t < -10 || (t > experimentTimeReference.getExperimentTime())) && fixDeviceTimeOffset == 0.0) {
                        Log.w("SensorInput", "Unrealistic time offset detected. Applying adjustment of " + -t + "s.");
                        fixDeviceTimeOffset = -t;
                    }
                    t += fixDeviceTimeOffset;
                    if (t < 0.0) {
                        Log.w("SensorInput", this.sensorName + ": Adjusted one timestamp from t = " + t + "s to t = 0s.");
                        t = 0.0;
                    }
                }
                dataT.append(t);
            }
            if (dataAbs != null)
                if (type == Sensor.TYPE_ROTATION_VECTOR)
                    dataAbs.append(Math.sqrt(aquisitions*aquisitions-avgX*avgX-avgY*avgY-avgZ*avgZ) / aquisitions);
                else
                    dataAbs.append(Math.sqrt(avgX*avgX+avgY*avgY+avgZ*avgZ) / aquisitions);
            if (dataAccuracy != null)
                dataAccuracy.append(accuracy);
        } finally {
            dataLock.unlock();
        }
    }

    private void resetAveraging(long t) {
        //Reset averaging
        avgX = 0.;
        avgY = 0.;
        avgZ = 0.;
        avgAccuracy = 0.;
        lastReading = t;
        aquisitions = 0;
    }

    public void updateGeneratedRate() {
        long now;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            now = SystemClock.elapsedRealtimeNanos();
        } else {
            now = SystemClock.elapsedRealtime() * 1000000L;
        }
        if (rateStrategy == SensorRateStrategy.generate && lastReading > 0) {
            while (lastReading + 2*period <= now) { //In case we did not get a sensor event in the last 200ms + 2*period, we fill it up here. This just ensures that the user gets data even with sensor types that do not update without a change.
                appendToBuffers(lastReading + period, genX, genY, genZ, genAccuracy);
                lastReading += period;
            }
        }
    }

    //This is called when we receive new data from a sensor. Append it to the right buffer
    public void onSensorChanged(SensorEvent event) {

        //From here only listen to "this" sensor
        if (event.sensor.getType() == sensor.getType()) {

            Double accuracy = Double.NaN;
            if (type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
                accuracy = 0.0;
            } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
                switch (event.accuracy) {
                    case SensorManager.SENSOR_STATUS_NO_CONTACT:
                    case SensorManager.SENSOR_STATUS_UNRELIABLE:
                        accuracy = -1.0;
                    case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                        accuracy = 1.0;
                    case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                        accuracy = 2.0;
                    case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                        accuracy = 3.0;
                }
            }

            if (rateStrategy == SensorRateStrategy.generate) {
                if (lastReading == 0) { //First value
                    genX = event.values[0];
                    genY = event.values.length > 1 ? event.values[1] : event.values[0];
                    genZ = event.values.length > 2 ? event.values[2] : event.values[0];
                    genAccuracy = accuracy;
                } else if (lastReading + period <= event.timestamp) {
                    while (lastReading + 2*period <= event.timestamp) {
                        appendToBuffers(lastReading + period, genX, genY, genZ, genAccuracy);
                        lastReading += period;
                    }
                    if (aquisitions > 0) {
                        genX = avgX / aquisitions;
                        genY = avgY / aquisitions;
                        genZ = avgZ / aquisitions;
                        genAccuracy = avgAccuracy;
                    }
                    appendToBuffers(lastReading + period, genX, genY, genZ, genAccuracy);
                    resetAveraging(lastReading + period);
                }
            }

            if (rateStrategy != SensorRateStrategy.request) {
                if (average) {
                    //We want averages, so sum up all the data and count the aquisitions
                    avgX += event.values[0];
                    if (event.values.length > 1) {
                        avgY += event.values[1];
                        if (event.values.length > 2)
                            avgZ += event.values[2];
                    }

                    avgAccuracy = Math.min(accuracy, avgAccuracy);
                    aquisitions++;
                } else {
                    //No averaging. Just keep the last result
                    avgX = event.values[0];
                    if (event.values.length > 1) {
                        avgY = event.values[1];
                        if (event.values.length > 2)
                            avgZ = event.values[2];
                    }
                    avgAccuracy = accuracy;
                    aquisitions = 1;
                }
                if (lastReading == 0)
                    lastReading = event.timestamp;
            }

            switch (rateStrategy) {
                case auto:
                    if (event.timestamp - lastReading < period * 0.9) {
                        if (lastOneTooFast)
                            rateStrategy = SensorRateStrategy.generate;
                        lastOneTooFast = true;
                    } else {
                        lastOneTooFast = false;
                    }
                    appendToBuffers(event.timestamp, event.values[0], event.values.length > 1 ? event.values[1] : event.values[0], event.values.length > 2 ? event.values[2] : event.values[0], accuracy);
                    resetAveraging(event.timestamp); //Keep averaging clean in case we need to change the strategy
                    break;
                case request:
                    appendToBuffers(event.timestamp, event.values[0], event.values.length > 1 ? event.values[1] : event.values[0], event.values.length > 2 ? event.values[2] : event.values[0], accuracy);
                    break;
                case generate:
                    //Done before averaging
                    break;
                case limit:
                    if (lastReading + period <= event.timestamp) {
                        appendToBuffers(event.timestamp, avgX / aquisitions, avgY / aquisitions, avgZ / aquisitions, avgAccuracy);
                        resetAveraging(event.timestamp);
                    }
                    break;
            }
        }
    }
}
