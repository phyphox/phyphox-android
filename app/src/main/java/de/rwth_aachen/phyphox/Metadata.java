package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Size;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity;
import de.rwth_aachen.phyphox.camera.helper.CameraHelper;
import de.rwth_aachen.phyphox.camera.depth.DepthInput;

import static android.content.Context.SENSOR_SERVICE;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.PREFS_NAME;

public class Metadata {

    public enum SensorMetadata {
        Name, Vendor, Range, Resolution, MinDelay, MaxDelay, Power, Version
    }

    public enum DeviceMetadata {
        uniqueID, version, build, fileFormat, deviceModel, deviceBrand, deviceBoard, deviceManufacturer, deviceBaseOS, deviceCodename, deviceRelease, sensorMetadata, depthFrontSensor, depthFrontResolution, depthFrontRate, depthBackSensor, depthBackResolution, depthBackRate, camera2api, camera2apiFull
    }

    public DeviceMetadata metadata;
    public SensorMetadata sensorMetadata = null;
    public SensorInput.SensorName sensor = null;

    String resultBuffer;

    //Identifiers are matched case-insensitively (see rules.yml, enum-case-insensitive), unknown
    //identifiers are still rejected with an IllegalArgumentException.
    public Metadata(String identifier, Context ctx) throws IllegalArgumentException {
        for (DeviceMetadata deviceMetadata : DeviceMetadata.values()) {
            if (deviceMetadata.name().equalsIgnoreCase(identifier)) {
                metadata = deviceMetadata;
                resultBuffer = getBuffered(ctx);
                return;
            }
        }
        String lowerIdentifier = identifier.toLowerCase();
        for (SensorInput.SensorName sensor : SensorInput.SensorName.values()) {
            //Custom sensors are selected by nameFilter, so per-sensor metadata for "custom" is
            //ambiguous and not part of the identifier vocabulary.
            if (sensor == SensorInput.SensorName.custom)
                continue;
            if (lowerIdentifier.startsWith(sensor.name().toLowerCase())) {
                String suffix = identifier.substring(sensor.name().length());
                for (SensorMetadata candidate : SensorMetadata.values()) {
                    if (candidate.name().equalsIgnoreCase(suffix)) {
                        sensorMetadata = candidate;
                        metadata = DeviceMetadata.sensorMetadata;
                        this.sensor = sensor;
                        resultBuffer = getBuffered(ctx);
                        return;
                    }
                }
            }
        }
        throw new IllegalArgumentException("Unknown metadata identifier: " + identifier);
    }

    //The camera and depth values below read the camera list CameraHelper caches. The experiment
    //list fills it when it loads, but nothing guarantees that a caller came that way - the
    //remote interface's /meta does not - and an unenumerated list would report a device without
    //any cameras.
    private static boolean readsCameraList(DeviceMetadata metadata) {
        switch (metadata) {
            case depthFrontSensor:
            case depthFrontResolution:
            case depthFrontRate:
            case depthBackSensor:
            case depthBackResolution:
            case depthBackRate:
            case camera2api:
            case camera2apiFull:
                return true;
            default:
                return false;
        }
    }

    public String getBuffered(Context ctx) {
        if (readsCameraList(metadata))
            CameraHelper.ensureCameraList((CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE));

        switch (metadata) {
            case uniqueID:
                final String settingName = "NetworkMetadataUUID";
                SharedPreferences settings = ctx.getSharedPreferences(PREFS_NAME, 0);
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
                    e.printStackTrace();
                    return "N/A";
                }
                return pInfo.versionName;
            }

            case build: {
                PackageInfo pInfo;
                try {
                    pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), PackageManager.GET_PERMISSIONS);
                } catch (Exception e) {
                    e.printStackTrace();
                    return "N/A";
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
                    SensorInput testSensor = new SensorInput(sensor.name(), null, -1, true, 0, SensorInput.SensorRateStrategy.auto, 0, false, null, null, null);
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
                            return String.valueOf(testSensor.sensor.getMaxDelay());
                        case Power:
                            return String.valueOf(testSensor.sensor.getPower());
                        case Version:
                            return String.valueOf(testSensor.sensor.getVersion());
                    }
                } catch (SensorInput.SensorException e) {
                    return null;
                }
                return null;

            case depthFrontSensor:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    return String.valueOf(DepthInput.countCameras(CameraCharacteristics.LENS_FACING_FRONT));
                return null;

            case depthBackSensor:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    return String.valueOf(DepthInput.countCameras(CameraCharacteristics.LENS_FACING_BACK));
                return null;

            case depthFrontResolution:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && DepthInput.isAvailable()) {
                    Size res = DepthInput.getMaxResolution(CameraCharacteristics.LENS_FACING_FRONT);
                    return String.valueOf(res.getWidth()) + "x" + String.valueOf(res.getHeight());
                }
                return null;

            case depthBackResolution:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && DepthInput.isAvailable()) {
                    Size res = DepthInput.getMaxResolution(CameraCharacteristics.LENS_FACING_BACK);
                    return String.valueOf(res.getWidth()) + "x" + String.valueOf(res.getHeight());
                }
                return null;

            case depthFrontRate:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && DepthInput.isAvailable())
                    return String.valueOf(DepthInput.getMaxRate(CameraCharacteristics.LENS_FACING_FRONT));
                return null;

            case depthBackRate:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && DepthInput.isAvailable())
                    return String.valueOf(DepthInput.getMaxRate(CameraCharacteristics.LENS_FACING_BACK));
                return null;

            case camera2api:
                return CameraHelper.getCamera2FormattedCaps(false);

            case camera2apiFull:
                return CameraHelper.getCamera2FormattedCaps(true);
        }
        return null;
    }

    public String get(String hash) {
        if (metadata == DeviceMetadata.uniqueID) {
            try {
                byte[] digest = MessageDigest.getInstance("MD5").digest((resultBuffer + hash).getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(digest.length * 2);
                for (byte b : digest)
                    hex.append(String.format("%02x", b));
                return hex.toString();
            } catch (NoSuchAlgorithmException e) {
                return null; //Cannot happen, MD5 is guaranteed to be available
            }
        } else {
            return resultBuffer;
        }
    }

}
