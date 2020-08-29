package de.rwth_aachen.phyphox.NetworkConnection;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.os.Build;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.UUID;

import de.rwth_aachen.phyphox.ExperimentList;
import de.rwth_aachen.phyphox.PhyphoxFile;
import de.rwth_aachen.phyphox.SensorInput;

import static android.content.Context.SENSOR_SERVICE;

public class NetworkMetadata {

    enum SensorMetadata {
        Name, Vendor, Range, Resolution, MinDelay, MaxDelay, Power, Version
    }

    enum Metadata {
        uniqueID, version, build, fileFormat, deviceModel, deviceBrand, deviceBoard, deviceManufacturer, deviceBaseOS, deviceCodename, deviceRelease, sensorMetadata
    }

    Metadata metadata;
    SensorMetadata sensorMetadata = null;
    SensorInput.SensorName sensor = null;

    String resultBuffer;

    public NetworkMetadata(String identifier, Context ctx) throws IllegalArgumentException {
        try {
            metadata = Metadata.valueOf(identifier);
            resultBuffer = getBuffered(ctx);
            return;
        } catch (IllegalArgumentException e) {
            for (SensorInput.SensorName sensor : SensorInput.SensorName.values()) {
                if (identifier.startsWith(sensor.name())) {
                    sensorMetadata = SensorMetadata.valueOf(identifier.substring(sensor.name().length()));
                    metadata = Metadata.sensorMetadata;
                    this.sensor = sensor;
                    resultBuffer = getBuffered(ctx);
                    return;
                }
            }
            throw e;
        }
    }

    public String getBuffered(Context ctx) {
        switch (metadata) {
            case uniqueID:
                final String settingName = "NetworkMetadataUUID";
                SharedPreferences settings = ctx.getSharedPreferences(ExperimentList.PREFS_NAME, 0);
                String userId = settings.getString(settingName, null);
                if (userId == null) {
                    userId = UUID.randomUUID().toString();
                    settings.edit().putString(settingName, userId).apply();
                }
                return userId;

            case version: {
                PackageInfo pInfo;
                try {
                    pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), PackageManager.GET_PERMISSIONS);
                } catch (Exception e) {
                    pInfo = null;
                }
                return pInfo.versionName;
            }

            case build: {
                PackageInfo pInfo;
                try {
                    pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), PackageManager.GET_PERMISSIONS);
                } catch (Exception e) {
                    pInfo = null;
                }
                return String.valueOf(pInfo.versionCode);
            }

            case fileFormat:
                return PhyphoxFile.phyphoxFileVersion;

            case deviceModel:
                return Build.MODEL;

            case deviceBrand:
                return Build.BRAND;

            case deviceBoard:
                return Build.BOARD;

            case deviceManufacturer:
                return Build.MANUFACTURER;

            case deviceBaseOS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    return Build.VERSION.BASE_OS;
                else
                    return null;

            case deviceCodename:
                return Build.VERSION.CODENAME;

            case deviceRelease:
                return Build.VERSION.RELEASE;

            case sensorMetadata:
                SensorManager sensorManager = (SensorManager) ctx.getSystemService(SENSOR_SERVICE);
                try {
                    SensorInput testSensor = new SensorInput(sensor.name(), true, 0, false, null, null, null);
                    testSensor.attachSensorManager(sensorManager);
                    if (testSensor.sensor == null)
                        return null;
                    switch (sensorMetadata) {
                        case Name:
                            return testSensor.sensor.getName();
                        case Vendor:
                            return testSensor.sensor.getVendor();
                        case Range:
                            return String.valueOf(testSensor.sensor.getMaximumRange());
                        case Resolution:
                            return String.valueOf(testSensor.sensor.getResolution());
                        case MinDelay:
                            return String.valueOf(testSensor.sensor.getMinDelay());
                        case MaxDelay:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                                return String.valueOf(testSensor.sensor.getMaxDelay());
                            else
                                return null;
                        case Power:
                            return String.valueOf(testSensor.sensor.getPower());
                        case Version:
                            return String.valueOf(testSensor.sensor.getVersion());
                    }
                } catch (SensorInput.SensorException e) {
                    return null;
                }
        }
        return null;
    }

    public String get(String hash) {
        if (metadata == Metadata.uniqueID) {
            return new String(Hex.encodeHex(DigestUtils.md5(resultBuffer + hash)));
        } else {
            return resultBuffer;
        }
    }

}
