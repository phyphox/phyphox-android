package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.BooleanSupplier;

import de.rwth_aachen.phyphox.camera.CameraInput;
import de.rwth_aachen.phyphox.camera.analyzer.AnalyzingModule;
import de.rwth_aachen.phyphox.camera.helper.WhiteBalance;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;
import de.rwth_aachen.phyphox.camera.model.CameraState;
import de.rwth_aachen.phyphox.camera.model.WhiteBalanceMethod;
import de.rwth_aachen.phyphox.camera.model.WhiteBalanceMode;

// phyphox-test: camera-white-balance
//The white balance of the camera input by white point (file format 1.21, phyphox-docs docs/file-format/input.md
//"White balance") on the running camera: the value-less lock engages the AWB lock when the measurement is first
//started and keeps it, a temperature with a tint is held as requested (clamped to what the camera reaches), the
//camera-gui control is disabled by either form and shows the value in effect, and on a camera without the CCT
//mode the shaders carry the adaptation to the requested white point. The colour math itself is covered offscreen
//in CameraAnalyzerMathTest; a controlled illuminant in front of the camera is not available here.
@RunWith(AndroidJUnit4.class)
public class CameraWhiteBalanceTest {
    private static final String LOCK = "camera-white-balance-lock.phyphox";
    private static final String TEMPERATURE = "camera-white-balance-temperature.phyphox";
    private static final String UNLOCKED = "camera-rgb.phyphox";

    private Experiment activity;

    @After
    public void closeFixture() {
        if (activity != null) {
            getInstrumentation().runOnMainSync(activity::stopMeasurement);
            FixtureExperiment.close(activity);
        }
    }

    private CameraInput launch(String fixture) throws Exception {
        assumeTrue("phyphox-docs was not checked out next to this repository at build time",
                FixtureExperiment.available(fixture));
        activity = FixtureExperiment.launch(fixture);
        CameraInput input = activity.experiment.cameraInput;
        assertNotNull("the fixture has a camera input", input);
        await("the camera did not start", () -> input.getCameraSettingState().getValue().getCameraState() == CameraState.RUNNING);
        return input;
    }

    private static void await(String failure, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline)
            Thread.sleep(100);
        assertTrue(failure, condition.getAsBoolean());
    }

    private void awaitFrames(String buffer, int frames) throws InterruptedException {
        await("the camera delivered fewer than " + frames + " frames in 30 s",
                () -> activity.experiment.getBuffer(buffer).getFilledSize() >= frames);
    }

    private String shownWhiteBalance() {
        TextView text = activity.findViewById(R.id.textWhiteBalance);
        assertNotNull("the camera-gui's white balance label", text);
        return text.getText().toString();
    }

    //The main controls are only laid out in the maximized view, where the enabled flags reach the views. The
    //zoom control is enabled at level 3 whatever the state, so it tells when the view state has been applied
    //(the main looper never idles with the preview running, so waitForIdleSync would hang).
    private void maximizePreview() throws InterruptedException {
        View maximize = activity.findViewById(R.id.imageMaximize);
        assertNotNull(maximize);
        getInstrumentation().runOnMainSync(maximize::performClick);
        View controls = activity.findViewById(R.id.cameraSetting);
        await("the camera controls did not appear", () -> controls.getVisibility() == View.VISIBLE);
        View zoom = activity.findViewById(R.id.lnrZoom);
        await("the control state was not applied", zoom::isEnabled);
    }

    @Test
    public void valuelessLockFreezesAtTheFirstStartAndDisablesTheControl() throws Exception {
        CameraInput input = launch(LOCK);
        CameraSettingState before = input.getCameraSettingState().getValue();
        assertEquals(WhiteBalanceMode.LOCKED, before.getWhiteBalanceMode());
        assertFalse("automatic until the first start", before.getWhiteBalanceFrozen());
        assertEquals("Locked", shownWhiteBalance());

        getInstrumentation().runOnMainSync(activity::startMeasurement);
        await("the lock was not engaged at the first start", () -> input.getCameraSettingState().getValue().getWhiteBalanceFrozen());
        awaitFrames("linearRed", 5);
        getInstrumentation().runOnMainSync(activity::stopMeasurement);
        assertTrue("the lock stays for the rest of the experiment", input.getCameraSettingState().getValue().getWhiteBalanceFrozen());
        assertNull("no adaptation in the shaders for a plain lock", AnalyzingModule.getWhiteBalance());

        maximizePreview();
        assertFalse("the white balance control is disabled", activity.findViewById(R.id.lnrWhiteBalance).isEnabled());
        assertTrue("the zoom control is not", activity.findViewById(R.id.lnrZoom).isEnabled());
    }

    @Test
    public void temperatureAndTintAreHeldAndShownInEffect() throws Exception {
        CameraInput input = launch(TEMPERATURE);
        CameraSettingState state = input.getCameraSettingState().getValue();
        assertEquals(WhiteBalanceMode.TEMPERATURE, state.getWhiteBalanceMode());
        assertEquals(3200, state.getWhiteBalanceTemperature());
        assertEquals(-0.004f, state.getWhiteBalanceTint(), 1e-9f);
        int lower = state.getWhiteBalanceTemperatureRange().getFirst();
        int upper = state.getWhiteBalanceTemperatureRange().getLast();
        int expected = Math.max(lower, Math.min(upper, 3200));
        assertEquals("clamped to what the camera reaches", expected, state.getWhiteBalanceTemperatureInEffect());
        assertEquals(expected + " K", shownWhiteBalance());

        getInstrumentation().runOnMainSync(activity::startMeasurement);
        awaitFrames("linearRed", 5);
        getInstrumentation().runOnMainSync(activity::stopMeasurement);

        state = input.getCameraSettingState().getValue();
        if (state.getWhiteBalanceMethod() == WhiteBalanceMethod.CCT) {
            assertNull("the camera balances on the CCT path, the shaders do not", AnalyzingModule.getWhiteBalance());
        } else {
            assertTrue(state.whiteBalanceCorrectionActive());
            assertNotNull("the shaders carry the adaptation", AnalyzingModule.getWhiteBalance());
            assertArrayEquals(WhiteBalance.INSTANCE.shaderMatrix(state.getWhiteBalanceTemperatureInEffect(), state.getWhiteBalanceTintInEffect()),
                    AnalyzingModule.getWhiteBalance(), 1e-7f);
            assertEquals("the lock only on a camera without a daylight anchor",
                    state.getWhiteBalanceMethod() == WhiteBalanceMethod.AWB_LOCK, state.getWhiteBalanceFrozen());
        }

        maximizePreview();
        assertFalse("the white balance control is disabled", activity.findViewById(R.id.lnrWhiteBalance).isEnabled());
    }

    @Test
    public void guiControlLocksAtOnceAndClampsATemperature() throws Exception {
        CameraInput input = launch(UNLOCKED);
        assertEquals(WhiteBalanceMode.AUTO, input.getCameraSettingState().getValue().getWhiteBalanceMode());
        assertFalse(input.getCameraSettingState().getValue().getWhiteBalanceLockedByFile());
        assertEquals("Auto", shownWhiteBalance());

        getInstrumentation().runOnMainSync(() -> input.setWhiteBalanceMode(WhiteBalanceMode.LOCKED));
        await("a lock chosen in the GUI freezes at once", () -> input.getCameraSettingState().getValue().getWhiteBalanceFrozen());
        assertEquals("Locked", shownWhiteBalance());

        getInstrumentation().runOnMainSync(() -> {
            input.setWhiteBalanceMode(WhiteBalanceMode.TEMPERATURE);
            input.setWhiteBalanceTemperature(1000000);
        });
        await("the temperature was not taken", () -> input.getCameraSettingState().getValue().getWhiteBalanceTemperature() == 1000000);
        CameraSettingState state = input.getCameraSettingState().getValue();
        int upper = state.getWhiteBalanceTemperatureRange().getLast();
        assertEquals("clamped to the top of the range", upper, state.getWhiteBalanceTemperatureInEffect());
        assertEquals("shown as clamped", upper + " K", shownWhiteBalance());

        getInstrumentation().runOnMainSync(() -> input.setWhiteBalanceMode(WhiteBalanceMode.AUTO));
        await("automatic again", () -> !input.getCameraSettingState().getValue().getWhiteBalanceFrozen()
                && input.getCameraSettingState().getValue().getWhiteBalanceMode() == WhiteBalanceMode.AUTO);
        assertEquals("Auto", shownWhiteBalance());

        maximizePreview();
        assertTrue("the white balance control is enabled at level 3", activity.findViewById(R.id.lnrWhiteBalance).isEnabled());

        //The panel opens from the button and closes with the controls when the preview leaves the exclusive view
        View panel = activity.findViewById(R.id.whiteBalanceControl);
        getInstrumentation().runOnMainSync(() -> activity.findViewById(R.id.lnrWhiteBalance).performClick());
        await("the white balance panel did not open", () -> panel.getVisibility() == View.VISIBLE);
        View minimize = activity.findViewById(R.id.imageMinimize);
        getInstrumentation().runOnMainSync(minimize::performClick);
        await("the white balance panel stayed open after leaving the exclusive view", () -> panel.getVisibility() != View.VISIBLE);
    }
}
