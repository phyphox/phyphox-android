package de.rwth_aachen.phyphox;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

//The sensorInput class encapsulates a sensor, maps their name from the phyphox-file format to
//  the android identifiers and handles their output, which is written to the dataBuffers
public class sensorInput implements SensorEventListener {
    public int type; //Sensor type (Android identifier)
    public int rate; //Sensor rate (Android constants: UI, normal, game or fastest)
    public long t0 = 0; //the start time of the measurement. This allows for timestamps relative to the beginning of a measurement
    public dataBuffer dataX; //Data-buffer for x
    public dataBuffer dataY; //Data-buffer for y (3D sensors only)
    public dataBuffer dataZ; //Data-buffer for z (3D sensors only)
    public dataBuffer dataT; //Data-buffer for t
    private SensorManager sensorManager; //Hold the sensor manager

    //The constructor needs the sensorManager, the phyphox identifier of the sensor type, the
    //desired aquisition rate (as string from the phyphox file: "ui", "normal", "game", "fastest"),
    //and the four buffers to receive x, y, z and t. The data buffers may be null to be left unused.
    protected sensorInput(SensorManager sensorManager, String type, String rate, dataBuffer bDataX, dataBuffer bDataY, dataBuffer bDataZ, dataBuffer bTime) {
        this.sensorManager = sensorManager; //Store the sensorManager reference

        //Interpret the rate string
        switch (rate) {
            case "fastest":
                this.rate = SensorManager.SENSOR_DELAY_FASTEST;
                break;
            case "game":
                this.rate = SensorManager.SENSOR_DELAY_GAME;
                break;
            case "normal":
                this.rate = SensorManager.SENSOR_DELAY_NORMAL;
                break;
            case "ui":
                this.rate = SensorManager.SENSOR_DELAY_UI;
                break;
        }

        //Interpret the type string
        switch (type) {
            case "linear_acceleration": this.type = Sensor.TYPE_LINEAR_ACCELERATION;
                break;
            case "light": this.type = Sensor.TYPE_LIGHT;
                break;
            case "gyroscope": this.type = Sensor.TYPE_GYROSCOPE;
                break;
            case "accelerometer": this.type = Sensor.TYPE_ACCELEROMETER;
                break;
            case "magnetic_field": this.type = Sensor.TYPE_MAGNETIC_FIELD;
                break;
            case "pressure": this.type = Sensor.TYPE_PRESSURE;
                break;
        }

        //Store the buffer references
        this.dataX = bDataX;
        this.dataY = bDataY;
        this.dataZ = bDataZ;
        this.dataT = bTime;
    }

    //Check if the sensor is available without trying to use it.
    public boolean isAvailable() {
        return (sensorManager.getDefaultSensor(type) != null);
    }

    //Get the internationalization string for a sensor type
    public int getDescriptionRes() {
        switch (type) {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return R.string.sensorLinearAcceleration;
            case Sensor.TYPE_LIGHT: this.type = Sensor.TYPE_LIGHT;
                return R.string.sensorLight;
            case Sensor.TYPE_GYROSCOPE: this.type = Sensor.TYPE_GYROSCOPE;
                return R.string.sensorGyroscope;
            case Sensor.TYPE_ACCELEROMETER: this.type = Sensor.TYPE_ACCELEROMETER;
                return R.string.sensorAccelerometer;
            case Sensor.TYPE_MAGNETIC_FIELD: this.type = Sensor.TYPE_MAGNETIC_FIELD;
                return R.string.sensorMagneticField;
            case Sensor.TYPE_PRESSURE: this.type = Sensor.TYPE_PRESSURE;
                return R.string.sensorPressure;
        }
        return R.string.unknown;
    }

    //Start the data aquisition by registering a listener for this sensor. t0 has to be set by the
    // caller, so it can set the same time base for all sensors
    public void start(long t0) {
        this.t0 = t0;
        this.sensorManager.registerListener(this, sensorManager.getDefaultSensor(type), rate);
    }

    //Stop the data aquisition by unregistering the listener for this sensor
    public void stop() {
        this.sensorManager.unregisterListener(this);
    }

    //This event listener is mandatory as this class implements SensorEventListener
    //But phyphox does not need it
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    //This is called when we receive new data from a sensor. Append it to the right buffer
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == type) {
            if (dataX != null)
                dataX.append(event.values[0]);
            if (dataY != null)
                dataY.append(event.values[1]);
            if (dataZ != null)
                dataZ.append(event.values[2]);
            if (dataT != null)
                dataT.append((event.timestamp-t0)*1e-9); //We want seconds since t0
        }
    }
}