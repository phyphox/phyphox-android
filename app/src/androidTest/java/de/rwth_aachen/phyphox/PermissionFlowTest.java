package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

// phyphox-test: app-chrome
//Both answers to a runtime permission request, on an experiment that needs the microphone.
//
//The permission state is arranged from OUTSIDE this run, and that is not a matter of taste:
//"pm grant" and "pm revoke" restart the app they apply to, and the instrumentation lives in that
//process - changing a permission from within kills the test run mid-test ("Process crashed").
//So the T1 job runs this class twice:
//
//    adb shell pm revoke de.rwth_aachen.phyphox android.permission.RECORD_AUDIO
//    adb shell am instrument -w -e class de.rwth_aachen.phyphox.PermissionFlowTest ...
//    adb shell pm grant de.rwth_aachen.phyphox android.permission.RECORD_AUDIO
//    adb shell am instrument -w -e class de.rwth_aachen.phyphox.PermissionFlowTest ...
//
//and the test asserts whichever path the current state calls for, naming it in the failure
//message so a single run is never mistaken for both.
//
//What the denied path asserts is what the app actually does: it does not put up the system
//request when the experiment opens, it refuses the experiment with "Need permission to record
//audio." and stays usable.
@RunWith(AndroidJUnit4.class)
public class PermissionFlowTest {

    private static final String AUDIO_EXPERIMENT = "audio_scope.phyphox";

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    private boolean microphoneGranted() {
        return ContextCompat.checkSelfPermission(getInstrumentation().getTargetContext(),
                android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @After
    public void closeWhateverIsOpen() {
        FixtureExperiment.close(FixtureExperiment.activity());
    }

    @Test
    public void theMicrophonePathMatchesThePermissionState() throws Exception {
        if (microphoneGranted())
            grantedPathLoadsTheExperiment();
        else
            deniedPathLeavesTheAppStanding();
    }

    private void grantedPathLoadsTheExperiment() {
        Experiment activity = FixtureExperiment.launchAsset(AUDIO_EXPERIMENT);
        assertTrue("with the microphone granted the audio experiment must load: "
                + activity.experiment.message, activity.experiment.loaded);
    }

    private void deniedPathLeavesTheAppStanding() throws Exception {
        FixtureExperiment.launchAssetWithoutWaiting(AUDIO_EXPERIMENT);
        Thread.sleep(5000);

        //Without the microphone the app does not ask again at this point - it refuses the
        //experiment and says why. What matters is that the refusal is legible and the app stays
        //usable; a blank screen or a crash would not be.
        assertTrue("the app is gone after opening an experiment it has no permission for",
                device().hasObject(By.pkg("de.rwth_aachen.phyphox").depth(0)));
        assertTrue("nothing explains why the experiment did not open",
                device().wait(Until.hasObject(By.textContains("permission")), 10000));
    }

}
