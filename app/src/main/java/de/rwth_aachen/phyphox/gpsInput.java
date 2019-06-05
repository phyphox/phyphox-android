package de.rwth_aachen.phyphox;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.Serializable;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

public class gpsInput implements Serializable {
    public dataBuffer dataLat; //Data-buffer for latitude
    public dataBuffer dataLon; //Data-buffer for longitude
    public dataBuffer dataZ; //Data-buffer for height
    public dataBuffer dataV; //Data-buffer for velocity
    public dataBuffer dataDir; //Data-buffer for direction
    public dataBuffer dataT; //Data-buffer for time

    public dataBuffer dataAccuracy; //Data-buffer for horizontal accuracy
    public dataBuffer dataZAccuracy; //Data-buffer for height accuracy
    public dataBuffer dataStatus; //Data-buffer for status codes (note filled parallel to the other buffers)
    public dataBuffer dataSatellites; //Data-buffer for status codes (note filled parallel to the other buffers)
    transient private LocationManager locationManager; //Hold the sensor manager

    public long t0 = 0; //the start time of the measurement. This allows for timestamps relative to the beginning of a measurement

    private Lock dataLock;
    private int lastStatus = 0;
    private double geoidCorrection = Double.NaN;

    public boolean forceGNSS = false;

    //The constructor
    protected gpsInput(Vector<dataOutput> buffers, Lock lock) {
        this.dataLock = lock;

        //Store the buffer references if any
        if (buffers == null)
            return;

        buffers.setSize(10);
        if (buffers.get(0) != null)
            this.dataLat = buffers.get(0).buffer;
        if (buffers.get(1) != null)
            this.dataLon = buffers.get(1).buffer;
        if (buffers.get(2) != null)
            this.dataZ = buffers.get(2).buffer;
        if (buffers.get(3) != null)
            this.dataV = buffers.get(3).buffer;
        if (buffers.get(4) != null)
            this.dataDir = buffers.get(4).buffer;
        if (buffers.get(5) != null)
            this.dataT = buffers.get(5).buffer;
        if (buffers.get(6) != null)
            this.dataAccuracy = buffers.get(6).buffer;
        if (buffers.get(7) != null)
            this.dataZAccuracy = buffers.get(7).buffer;
        if (buffers.get(8) != null)
            this.dataStatus = buffers.get(8).buffer;
        if (buffers.get(9) != null)
            this.dataSatellites = buffers.get(9).buffer;
    }

    public void attachLocationManager(LocationManager locationManager) {
        this.locationManager = locationManager;
    }

    //Check if GPS hardware is available
    public static boolean isAvailable(Context context) {
        return (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS));
    }

    //Start the data aquisition by registering a listener for this location manager.
    public void start() {
        this.t0 = 0; //Reset t0. This will be set by the first sensor event
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            this.lastStatus = 0;
        else
            this.lastStatus = -1;

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            locationManager.addNmeaListener(nmeaListener);
            if (!forceGNSS)
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        } catch (SecurityException e) {
            Log.e("gps start", "Security exception when requesting location updates: " + e.getMessage());
        }
        onStatusUpdate(0);
    }

    //Stop the data aquisition by unregistering the listener for this location manager
    public void stop() {
        locationManager.removeUpdates(locationListener);
        locationManager.removeNmeaListener(nmeaListener);
    }

    LocationListener locationListener = new LocationListener() {
        //Mandatory for LocationListener
        public void onProviderDisabled(String provider) {
            onStatusUpdate(0);
        }
        public void onProviderEnabled(String provider) {
            onStatusUpdate(0);
        }
        public void onStatusChanged(String provider, int status, Bundle extras) {
            onStatusUpdate(status);
        }

        public void onLocationChanged(Location location) {
            // Called when a new location is found by the network location provider.
            onSensorChanged(location);
        }
    };

    GpsStatus.NmeaListener nmeaListener = new GpsStatus.NmeaListener() {
        @Override
        public void onNmeaReceived(long timestamp, String nmea) {
            if (nmea.length() > 19 && nmea.substring(3, 6).equals("GGA")) {
                String[] parts = nmea.split(",");
                if (parts.length < 10)
                    return;
                try {
                    geoidCorrection = Double.parseDouble(parts[11]);
                } catch (Exception e) {
                    return;
                }
            }
        }
    };

    public void onStatusUpdate(int status) {
        if (dataStatus == null)
            return;

        dataLock.lock();
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                if (status == LocationProvider.AVAILABLE)
                    if (Double.isNaN(geoidCorrection))
                        dataStatus.append(2);
                    else
                        dataStatus.append(1);
                else
                    dataStatus.append(0);
            else
                dataStatus.append(-1);
        } finally {
            dataLock.unlock();
        }
    }

    //This is called when we receive new data from a sensor. Append it to the right buffer
    public void onSensorChanged(Location event) {
        if (t0 == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                t0 = event.getElapsedRealtimeNanos();
                if (dataT != null && dataT.getFilledSize() > 0)
                    t0 -= dataT.value * 1e9;
            } else {
                t0 = event.getTime(); //Old API does not provide ElapsedRealtimeNanos
                if (dataT != null && dataT.getFilledSize() > 0)
                    t0 -= dataT.value * 1e3;
            }
        }

        //Append the data to available buffers
        dataLock.lock();
        try {
            if (dataT != null) {
                double newT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    newT = (event.getElapsedRealtimeNanos() - t0) * 1e-9;
                else
                    newT = (event.getTime() - t0) * 1e-3;
                if (newT < dataT.value)
                    return;
                dataT.append(newT);
            }

            if (dataLat != null)
                dataLat.append(event.getLatitude());
            if (dataLon != null)
                dataLon.append(event.getLongitude());
            if (dataZ != null) {
                if (Double.isNaN(geoidCorrection)) {
                    dataZ.append(event.getAltitude());
                } else {
                    dataZ.append(event.getAltitude() - geoidCorrection);
                }
            }
            if (dataV != null)
                dataV.append(event.getSpeed());
            if (dataDir != null)
                dataDir.append(event.getBearing());

            if (dataAccuracy != null)
                dataAccuracy.append(event.getAccuracy());
            if (dataZAccuracy != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    dataZAccuracy.append(event.getVerticalAccuracyMeters());
                } else
                    dataZAccuracy.append(0); //Older Android does not provide vertical accuracy
            }
            if (dataSatellites != null) {
                if (event.getExtras() != null)  //Not sure why this might happen, but there seem to be rare cases in which this leads to a crash
                    dataSatellites.append(event.getExtras().getInt("satellites", 0));
                else
                    dataSatellites.append(0);
            }
        } finally {
            dataLock.unlock();
        }

    }
}
