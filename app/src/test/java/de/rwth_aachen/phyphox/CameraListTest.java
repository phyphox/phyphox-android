package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowCameraCharacteristics;

import java.lang.reflect.Field;

import de.rwth_aachen.phyphox.camera.depth.DepthInput;
import de.rwth_aachen.phyphox.camera.helper.CameraHelper;

//CameraHelper caches the device's camera characteristics, and the depth input and the device
//metadata read that cache. The experiment list fills it when it loads, but nothing guarantees
//that a caller came that way - the remote interface's /meta does not - so the cache must be
//readable before anyone enumerated, and fillable on demand.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CameraListTest {

    //The cached list is app state, which Robolectric does not reset between test methods, so
    //start each of them from the state a freshly started app is in: nothing enumerated yet.
    @Before
    public void forgetEnumeratedCameras() throws Exception {
        Field field = CameraHelper.class.getDeclaredField("cameraList");
        field.setAccessible(true);
        field.set(CameraHelper.INSTANCE, null);
    }

    //A camera manager with one back camera that reports depth output, added to the shadow but
    //deliberately not enumerated into CameraHelper.
    private CameraManager cameraManagerWithDepthCamera() {
        Application application = ApplicationProvider.getApplicationContext();
        CameraManager cameraManager = (CameraManager) application.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = ShadowCameraCharacteristics.newCameraCharacteristics();
        ShadowCameraCharacteristics shadow = Shadows.shadowOf(characteristics);
        shadow.set(CameraCharacteristics.LENS_FACING, CameraMetadata.LENS_FACING_BACK);
        shadow.set(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES, new int[]{
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT});
        Shadows.shadowOf(cameraManager).addCamera("0", characteristics);
        return cameraManager;
    }

    @Test
    public void unenumeratedListIsEmptyRatherThanNull() {
        CameraManager cameraManager = cameraManagerWithDepthCamera();

        assertTrue("An unenumerated camera list reads as empty, never as null",
                CameraHelper.getCameraList().isEmpty());
        //The readers must cope with that instead of throwing - this is what turned every /meta
        //request into a 500 when the list had not been filled.
        assertFalse(DepthInput.isAvailable());
        assertEquals(0, DepthInput.countCameras(CameraCharacteristics.LENS_FACING_BACK));
        assertEquals("[]", CameraHelper.getCamera2FormattedCaps(false));

        CameraHelper.ensureCameraList(cameraManager);
        assertEquals("ensureCameraList enumerates what has not been enumerated yet",
                1, CameraHelper.getCameraList().size());
        assertTrue(DepthInput.isAvailable());
    }

    @Test
    public void camerasWithoutCharacteristicsAreSkipped() {
        Application application = ApplicationProvider.getApplicationContext();
        CameraManager cameraManager = (CameraManager) application.getSystemService(Context.CAMERA_SERVICE);
        //A camera that reports nothing at all. Real hardware always reports more, but the scans
        //run over whatever the device lists, and /meta must survive a device that is stingy.
        Shadows.shadowOf(cameraManager).addCamera("0", ShadowCameraCharacteristics.newCameraCharacteristics());
        CameraHelper.updateCameraList(cameraManager);

        assertEquals(1, CameraHelper.getCameraList().size());
        assertFalse(DepthInput.isAvailable());
        assertEquals(0, DepthInput.countCameras(-1));
        assertEquals(0, DepthInput.countCameras(CameraCharacteristics.LENS_FACING_BACK));
        assertEquals(new android.util.Size(0, 0), DepthInput.getMaxResolution(-1));
        assertEquals(0.f, DepthInput.getMaxRate(-1), 0.f);
        assertEquals(null, DepthInput.findCamera(-1));
        //The capability report lists the camera, just without the values it does not report.
        assertTrue(CameraHelper.getCamera2FormattedCaps(false).contains("\"id\":\"0\""));
    }
}
