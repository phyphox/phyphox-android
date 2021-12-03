package de.rwth_aachen.phyphox.Camera;

import android.content.res.Resources;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.ArraySet;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public abstract class CameraHelper {
    private static Map<String, CameraCharacteristics> cameraList = null;

    public static void updateCameraList(CameraManager cm) {
        cameraList = new HashMap<>();
        try {
            for (String cameraId : cm.getCameraIdList()) {
                try {
                    cameraList.put(cameraId, cm.getCameraCharacteristics(cameraId));
                } catch (CameraAccessException e) {
                    //If a single camera is unavailable, skip it.
                }
            }
        } catch (CameraAccessException e) {
            //That's it. If no camera is available, the list shall remain empty
        }
    }

    public static Map<String, CameraCharacteristics> getCameraList() {
        return cameraList;
    }

    public static String facingConstToString(int c) {
        switch (c) {
            case CameraCharacteristics.LENS_FACING_FRONT:
                return "LENS_FACING_FRONT";
            case CameraCharacteristics.LENS_FACING_BACK:
                return "LENS_FACING_BACK";
            case CameraCharacteristics.LENS_FACING_EXTERNAL:
                return "LENS_FACING_EXTERNAL";
        }
        return String.valueOf(c);
    }

    public static String hardwareLevelConstToString(int c) {
        switch (c) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                return "HARDWARE_LEVEL_LIMITED";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                return "HARDWARE_LEVEL_FULL";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                return "HARDWARE_LEVEL_LEGACY";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                return "HARDWARE_LEVEL_3";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL:
                return "HARDWARE_LEVEL_EXTERNAL";
        }
        return String.valueOf(c);
    }

    public static String capabilityConstToString(int c) {
        switch (c) {
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE:
                return "CAPABILITIES_BACKWARD_COMPATIBLE";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR:
                return "CAPABILITIES_MANUAL_SENSOR";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING:
                return "CAPABILITIES_MANUAL_POST_PROCESSING";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW:
                return "CAPABILITIES_RAW";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING:
                return "CAPABILITIES_PRIVATE_REPROCESSING";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS:
                return "CAPABILITIES_READ_SENSOR_SETTINGS";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE:
                return "CAPABILITIES_BURST_CAPTURE";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING:
                return "CAPABILITIES_YUV_REPROCESSING";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT:
                return "CAPABILITIES_DEPTH_OUTPUT";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO:
                return "CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING:
                return "CAPABILITIES_MOTION_TRACKING";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA:
                return "CAPABILITIES_LOGICAL_MULTI_CAMERA";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME:
                return "CAPABILITIES_MONOCHROME";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA:
                return "CAPABILITIES_SECURE_IMAGE_DATA";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA:
                return "CAPABILITIES_SYSTEM_CAMERA";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING:
                return "CAPABILITIES_OFFLINE_PROCESSING";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR:
                return "CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING:
                return "CAPABILITIES_REMOSAIC_REPROCESSING";
        }
        return String.valueOf(c);
    }

    public static String getCamera2FormattedCaps(Boolean full) {
        JSONArray json = new JSONArray();

        for (Map.Entry<String, CameraCharacteristics> cam : cameraList.entrySet()) {
            JSONObject jsonCam = new JSONObject();

            try {
                jsonCam.put("id", cam.getKey());
                jsonCam.put("facing", facingConstToString(cam.getValue().get(CameraCharacteristics.LENS_FACING)));
                jsonCam.put("hardwareLevel", hardwareLevelConstToString(cam.getValue().get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)));

                int[] caps = cam.getValue().get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                JSONArray jsonCaps = new JSONArray();
                if (caps != null) {
                    for (int cap : caps)
                        jsonCaps.put(capabilityConstToString(cap));
                }
                jsonCam.put("capabilities", jsonCaps);

                if (full) {

                    JSONArray jsonCapRequestKeys = new JSONArray();
                    List<CaptureRequest.Key<?>> captureRequestKeys = cam.getValue().getAvailableCaptureRequestKeys();
                    if (captureRequestKeys != null) {
                        for (CaptureRequest.Key<?> key : captureRequestKeys) {
                            jsonCapRequestKeys.put(key.getName());
                        }
                    }
                    jsonCam.put("captureRequestKeys", jsonCapRequestKeys);

                    JSONArray jsonCapResultKeys = new JSONArray();
                    List<CaptureResult.Key<?>> captureResultKeys = cam.getValue().getAvailableCaptureResultKeys();
                    if (captureResultKeys != null) {
                        for (CaptureResult.Key<?> key : captureResultKeys) {
                            jsonCapResultKeys.put(key.getName());
                        }
                    }
                    jsonCam.put("captureResultKeys", jsonCapRequestKeys);

                    JSONArray jsonFpsRanges = new JSONArray();
                    Range<Integer>[] fpsRanges = cam.getValue().get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    for (Range<Integer> fpsRange : fpsRanges) {
                        JSONObject jsonFpsRange = new JSONObject();
                        jsonFpsRange.put("min", fpsRange.getLower());
                        jsonFpsRange.put("max", fpsRange.getUpper());
                        jsonFpsRanges.put(jsonFpsRange);
                    }
                    jsonCam.put("fpsRanges", jsonFpsRanges);

                    JSONArray jsonPhysicalCamIds = new JSONArray();
                    Set<String> physicalCamIds;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        physicalCamIds = cam.getValue().getPhysicalCameraIds();
                    else
                        physicalCamIds = new HashSet<>();
                    for (String physicalCamId : physicalCamIds) {
                        jsonPhysicalCamIds.put(physicalCamId);
                    }
                    jsonCam.put("physicalCamIds", jsonPhysicalCamIds);

                    JSONArray jsonStreamConfigs = new JSONArray();
                    StreamConfigurationMap configMap = cam.getValue().get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    int[] formats = configMap.getOutputFormats();
                    for (Integer format : formats) {
                        JSONObject jsonFormat = new JSONObject();
                        jsonFormat.put("format", format);

                        JSONArray jsonSizes = new JSONArray();
                        Size[] sizes = configMap.getOutputSizes(format);
                        for (Size size : sizes) {
                            JSONObject jsonSize = new JSONObject();
                            jsonSize.put("w", size.getWidth());
                            jsonSize.put("h", size.getHeight());
                            jsonSizes.put(jsonSize);
                        }
                        jsonFormat.put("outputSizes", jsonSizes);

                        JSONArray jsonHighspeed = new JSONArray();
                        Size[] highSpeedVideoSizes = configMap.getHighSpeedVideoSizes();
                        for (Size size : highSpeedVideoSizes) {
                            JSONObject jsonSize = new JSONObject();
                            jsonSize.put("w", size.getWidth());
                            jsonSize.put("h", size.getHeight());

                            JSONArray jsonHighSpeedVideoFpsRanges = new JSONArray();
                            Range<Integer>[] highSpeedVideoFpsRange = configMap.getHighSpeedVideoFpsRangesFor(size);
                            for (Range<Integer> fpsRange : highSpeedVideoFpsRange) {
                                JSONObject jsonFpsRange = new JSONObject();
                                jsonFpsRange.put("min", fpsRange.getLower());
                                jsonFpsRange.put("max", fpsRange.getUpper());
                                jsonHighSpeedVideoFpsRanges.put(jsonFpsRange);
                            }
                            jsonSize.put("fpsRanges", jsonHighSpeedVideoFpsRanges);

                            jsonHighspeed.put(jsonSize);
                        }
                        jsonFormat.put("highspeed", jsonHighspeed);

                        jsonStreamConfigs.put(jsonFormat);
                    }
                    jsonCam.put("streamConfigurations", jsonStreamConfigs);
                }
                json.put(jsonCam);
            } catch (JSONException e) {
                try {
                    jsonCam.put("error", e.getMessage());
                } catch (JSONException e2) {
                    Log.e("CameraHelper", "Severe JSON error when creating camera2api caps string: " + e2.getMessage());
                }
            }
        }
        return json.toString();
    }
}
