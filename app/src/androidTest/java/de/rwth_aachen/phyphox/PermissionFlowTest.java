package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

// phyphox-test: app-chrome
//Both answers to a runtime permission request. The permission state is arranged from OUTSIDE:
//"pm grant"/"pm revoke" restart the app, and the instrumentation lives in that process, so the
//T1 job runs this class twice (revoked, then granted) and the test asserts whichever path applies.
@RunWith(AndroidJUnit4.class)
public class PermissionFlowTest {

    private static final String AUDIO_EXPERIMENT = "audio_scope.phyphox";
    private static final String PACKAGE = "de.rwth_aachen.phyphox";

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

        //Either the system dialog, or - where Android has stopped asking - phyphox's own refusal.
        UiObject2 deny = device().wait(Until.findObject(By.textStartsWith("Don")), 20000);
        if (deny != null) {
            //Answered until gone: a dialog still animating in swallows the tap (seen on the tablet profile).
            long deadline = System.currentTimeMillis() + 20000;
            boolean gone = false;
            while (!gone && System.currentTimeMillis() < deadline) {
                UiObject2 button = device().findObject(By.textStartsWith("Don"));
                if (button == null) {
                    gone = true;
                    break;
                }
                try {
                    button.click();
                } catch (StaleObjectException e) {
                    //the dialog changed under the tap - look it up again
                }
                gone = device().wait(Until.gone(By.textStartsWith("Don")), 5000);
            }
            assertTrue("the permission dialog stayed after it was answered", gone);
        }

        assertTrue("the app is not in the foreground after the microphone was refused (top "
                        + "package: " + topPackage() + ")",
                device().wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), 15000));
        assertTrue("nothing explains why the experiment did not open",
                device().wait(Until.hasObject(By.textContains("permission")), 15000));
    }

    private String topPackage() {
        UiObject2 root = device().findObject(By.depth(0));
        return root == null ? "none" : String.valueOf(root.getApplicationPackage());
    }

}
