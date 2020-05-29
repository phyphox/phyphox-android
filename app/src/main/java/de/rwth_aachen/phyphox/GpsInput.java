package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.Serializable;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

public class GpsInput implements Serializable {
    public DataBuffer dataLat; //Data-buffer for latitude
    public DataBuffer dataLon; //Data-buffer for longitude
    public DataBuffer dataZ; //Data-buffer for height
    public DataBuffer dataZWGS84; //Data-buffer for height above WGS84 ellipsoid
    public DataBuffer dataV; //Data-buffer for velocity
    public DataBuffer dataDir; //Data-buffer for direction
    public DataBuffer dataT; //Data-buffer for time

    public DataBuffer dataAccuracy; //Data-buffer for horizontal accuracy
    public DataBuffer dataZAccuracy; //Data-buffer for height accuracy
    public DataBuffer dataStatus; //Data-buffer for status codes (note filled parallel to the other buffers)
    public DataBuffer dataSatellites; //Data-buffer for status codes (note filled parallel to the other buffers)
    transient private LocationManager locationManager; //Hold the sensor manager

    public long t0 = 0; //the start time of the measurement. This allows for timestamps relative to the beginning of a measurement
    public double lastSatBasedLocation;

    private Lock dataLock;
    private int lastStatus = 0;
    private GpsGeoid geoid;


    public boolean forceGNSS = false;

    //The constructor
    protected GpsInput(Vector<DataOutput> buffers, Lock lock) {
        this.dataLock = lock;

        //Store the buffer references if any
        if (buffers == null)
            return;

        buffers.setSize(11);
        if (buffers.get(0) != null)
            this.dataLat = buffers.get(0).buffer;
        if (buffers.get(1) != null)
            this.dataLon = buffers.get(1).buffer;
        if (buffers.get(2) != null)
            this.dataZ = buffers.get(2).buffer;
        if (buffers.get(3) != null)
            this.dataZWGS84 = buffers.get(3).buffer;
        if (buffers.get(4) != null)
            this.dataV = buffers.get(4).buffer;
        if (buffers.get(5) != null)
            this.dataDir = buffers.get(5).buffer;
        if (buffers.get(6) != null)
            this.dataT = buffers.get(6).buffer;
        if (buffers.get(7) != null)
            this.dataAccuracy = buffers.get(7).buffer;
        if (buffers.get(8) != null)
            this.dataZAccuracy = buffers.get(8).buffer;
        if (buffers.get(9) != null)
            this.dataStatus = buffers.get(9).buffer;
        if (buffers.get(10) != null)
            this.dataSatellites = buffers.get(10).buffer;
    }

    public void attachLocationManager(LocationManager locationManager) {
        this.locationManager = locationManager;
    }

    public void prepare(Resources res) {
        geoid = new GpsGeoid(res.openRawResource(R.raw.egm84_30));
    }

    //Check if GPS hardware is available
    public static boolean isAvailable(Context context) {
        return (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS));
    }

    //Start the data aquisition by registering a listener for this location manager.
    public void start() {
        this.t0 = 0; //Reset t0. This will be set by the first sensor event
        lastSatBasedLocation = -100.0; //We did not yet have a GNSS-based location
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            this.lastStatus = 0;
        else
            this.lastStatus = -1;

        try {
            if (locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            if (!forceGNSS && locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        } catch (SecurityException e) {
            Log.e("gps start", "Security exception when requesting location updates: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.e("gps start", "Illegal argument despite existing provider: " + e.getMessage());
        }
        onStatusUpdate(0);
    }

    //Stop the data aquisition by unregistering the listener for this location manager
    public void stop() {
        locationManager.removeUpdates(locationListener);
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

    public void onStatusUpdate(int status) {
        if (dataStatus == null)
            return;

        dataLock.lock();
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                if (status == LocationProvider.AVAILABLE)
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

        double newT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            newT = (event.getElapsedRealtimeNanos() - t0) * 1e-9;
        else
            newT = (event.getTime() - t0) * 1e-3;
        if (dataT != null && newT < dataT.value)
            return;

        if (event.getProvider().equals(LocationManager.GPS_PROVIDER))
            lastSatBasedLocation = newT;
        else { //To if we have a recent GNSS-based location, we ignore other providers for a while as they are not necessary and make data interpretation unnecessary complicated, especially for students who do not know about the different location providers
            if (newT - lastSatBasedLocation < 10)
                return;
        }

        //Append the data to available buffers
        dataLock.lock();
        try {
            if (dataT != null)
                dataT.append(newT);
            if (dataLat != null)
                dataLat.append(event.getLatitude());
            if (dataLon != null)
                dataLon.append(event.getLongitude());
            if (dataZWGS84 != null) {
                dataZWGS84.append(event.getAltitude());
            }
            if (dataZ != null) {
                dataZ.append(event.getAltitude() != 0 ? event.getAltitude() - geoid.height(event.getLatitude(), event.getLongitude()) : 0.0);
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
