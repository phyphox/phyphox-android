package de.rwth_aachen.phyphox;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Created by dicon on 19.06.15.
 */
public class sensorInput implements SensorEventListener {
    public int type;
    public int rate;
    public long t0;
    public dataBuffer dataX;
    public dataBuffer dataY;
    public dataBuffer dataZ;
    public dataBuffer dataT;
    private SensorManager sensorManager;

    protected sensorInput(SensorManager sensorManager, String type, String rate, dataBuffer bDataX, dataBuffer bDataY, dataBuffer bDataZ, dataBuffer bTime) {
        this.sensorManager = sensorManager;
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
        this.t0 = 0;
        this.dataX = bDataX;
        this.dataY = bDataY;
        this.dataZ = bDataZ;
        this.dataT = bTime;
    }

    public boolean isAvailable() {
        return (sensorManager.getDefaultSensor(type) != null);
    }

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

    public void start(long t0) {
        this.t0 = t0;
        this.sensorManager.registerListener(this, sensorManager.getDefaultSensor(type), rate);
    }

    public void stop() {
        this.sensorManager.unregisterListener(this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == type) {
            if (dataX != null)
                dataX.append(event.values[0]);
            if (dataY != null)
                dataY.append(event.values[1]);
            if (dataZ != null)
                dataZ.append(event.values[2]);
            if (dataT != null)
                dataT.append((event.timestamp-t0)*1e-9);
        }
    }
}