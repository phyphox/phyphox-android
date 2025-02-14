package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.util.Size;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.UUID;

import de.rwth_aachen.phyphox.camera.helper.CameraHelper;
import de.rwth_aachen.phyphox.camera.depth.DepthInput;

import static android.content.Context.SENSOR_SERVICE;

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

    public Metadata(String identifier, Context ctx) throws IllegalArgumentException {
        try {
            metadata = DeviceMetadata.valueOf(identifier);
            resultBuffer = getBuffered(ctx);
            return;
        } catch (IllegalArgumentException e) {
            for (SensorInput.SensorName sensor : SensorInput.SensorName.values()) {
                if (identifier.startsWith(sensor.name())) {
                    sensorMetadata = SensorMetadata.valueOf(identifier.substring(sensor.name().length()));
                    metadata = DeviceMetadata.sensorMetadata;
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
                SharedPreferences settings = ctx.getSharedPreferences(ExperimentListActivity.PREFS_NAME, 0);
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
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                    return null;
                return CameraHelper.getCamera2FormattedCaps(false);

            case camera2apiFull:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                    return null;
                return CameraHelper.getCamera2FormattedCaps(true);
        }
        return null;
    }

    public String get(String hash) {
        if (metadata == DeviceMetadata.uniqueID) {
            return new String(Hex.encodeHex(DigestUtils.md5(resultBuffer + hash)));
        } else {
            return resultBuffer;
        }
    }

}
