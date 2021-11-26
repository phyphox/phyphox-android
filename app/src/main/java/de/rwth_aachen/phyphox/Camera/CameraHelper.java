package de.rwth_aachen.phyphox.Camera;

import android.content.res.Resources;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
