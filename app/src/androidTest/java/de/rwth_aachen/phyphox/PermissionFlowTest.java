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
//The denied path has two shapes, and which one appears is up to the device rather than the app:
//where the microphone has never been refused for good, phyphox asks and the system dialog comes
//up; where Android has stopped asking, phyphox refuses the experiment itself with "Need
//permission to record audio.". The test answers the dialog if it appears and then asserts the
//same ending either way - phyphox in front, saying why the experiment did not open.
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

        //Two shapes, depending on what the device remembers. On a fresh device phyphox asks for
        //the microphone and the system dialog comes up; where the request was already refused
        //for good, Android does not ask again and phyphox refuses the experiment itself. Both
        //end the same way, and that ending is what this pins.
        UiObject2 deny = device().wait(Until.findObject(By.textStartsWith("Don")), 20000);
        if (deny != null) {
            //Answered until it is really gone, rather than tapped once. A dialog that is still
            //animating in swallows the tap, and the app cannot put it back up - the denied branch
            //of onRequestPermissionsResult does nothing at all - so a dialog that is still there
            //means the tap did not land. Seen on the tablet profile in CI.
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
                    //The dialog changed under the tap - look it up again and answer that one
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
