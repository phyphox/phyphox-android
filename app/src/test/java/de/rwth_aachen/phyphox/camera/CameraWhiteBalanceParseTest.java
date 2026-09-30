package de.rwth_aachen.phyphox.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import de.rwth_aachen.phyphox.CorpusTestEnvironment;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;
import de.rwth_aachen.phyphox.camera.model.WhiteBalanceMode;

//The white_balance and white_balance_tint entries of the camera input's locked attribute (file format 1.21,
//phyphox-docs docs/file-format/input.md "White balance") on the two generated corpus fixtures: the value-less
//form asks for the automatic result to be frozen at the first start, the valued form for a colour temperature
//with a Duv tint, either disables the camera-gui control. What the camera then does is the T1 row
//camera-white-balance.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CameraWhiteBalanceParseTest {

    private static CameraInput load(String fixture) throws Exception {
        return CorpusTestEnvironment.loadGeneratedFixture(fixture).cameraInput;
    }

    @Test
    public void valuelessEntryFreezesAtTheFirstStart() throws Exception {
        CameraInput input = load("camera-white-balance-lock.phyphox");
        CameraSettingState state = input.getCameraSettingState().getValue();
        assertEquals(WhiteBalanceMode.LOCKED, state.getWhiteBalanceMode());
        assertTrue("the camera-gui control is disabled", state.getWhiteBalanceLockedByFile());
        assertFalse("the automatic white balance runs until the first start", state.getWhiteBalanceFrozen());
        assertEquals("", input.getLockedSettings().get("white_balance"));
    }

    @Test
    public void temperatureAndTintAreReadInKelvinAndDuv() throws Exception {
        CameraInput input = load("camera-white-balance-temperature.phyphox");
        CameraSettingState state = input.getCameraSettingState().getValue();
        assertEquals(WhiteBalanceMode.TEMPERATURE, state.getWhiteBalanceMode());
        assertEquals(3200, state.getWhiteBalanceTemperature());
        assertEquals(-0.004f, state.getWhiteBalanceTint(), 1e-9f);
        assertEquals("nothing to clamp at 3200 K", 3200, state.getWhiteBalanceTemperatureInEffect());
        assertEquals(-0.004f, state.getWhiteBalanceTintInEffect(), 1e-9f);
        assertTrue("the camera-gui control is disabled", state.getWhiteBalanceLockedByFile());
        assertFalse("no camera yet, so no lock either", state.getWhiteBalanceFrozen());
    }

    @Test
    public void noEntryLeavesTheAutomaticWhiteBalance() throws Exception {
        CameraSettingState state = load("camera-rgb.phyphox").getCameraSettingState().getValue();
        assertEquals(WhiteBalanceMode.AUTO, state.getWhiteBalanceMode());
        assertFalse(state.getWhiteBalanceLockedByFile());
        assertFalse(state.whiteBalanceCorrectionActive());
    }
}
