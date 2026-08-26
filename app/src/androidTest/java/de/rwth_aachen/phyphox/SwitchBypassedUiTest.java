package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// phyphox-test: switch-bypassed-ui
//Everything the host-controlled switches bypass has to work without them - a switch that hides a
//broken user path is worse than no switch at all. So this suite runs with debug.phyphox.remote
//and debug.phyphox.autoConfirm explicitly cleared, and drives the real UI:
//
//  - the menu toggle brings the remote server up, confirmation dialog and all;
//  - opening a network experiment shows the privacy notice, which is informational: its single
//    OK dismisses it, and a decline control turning up here would be a finding (nothing
//    sensitive is recorded by opening an experiment, and a connection before the start has
//    nothing to transmit);
//  - a downloaded experiment offers to be saved to the collection, and the offer works both
//    ways.
@RunWith(AndroidJUnit4.class)
public class SwitchBypassedUiTest {

    private static final int PORT = 8080;

    @Before
    public void clearSwitches() throws Exception {
        //The point of this suite: nothing here may depend on them.
        shell("setprop debug.phyphox.remote '\"\"'");
        shell("setprop debug.phyphox.remotePort '\"\"'");
        shell("setprop debug.phyphox.autoConfirm '\"\"'");
    }

    @After
    public void closeWhateverIsOpen() {
        //Not "am force-stop": the instrumentation runs inside the app's process, so stopping the
        //app kills the test run with it ("Process crashed", no results).
        FixtureExperiment.close(FixtureExperiment.activity());
    }

    private void shell(String command) throws Exception {
        UiDevice.getInstance(getInstrumentation()).executeShellCommand(command);
    }

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    private boolean remoteApiAnswers() {
        try {
            HttpURLConnection connection = (HttpURLConnection)
                    new URL("http://127.0.0.1:" + PORT + "/config").openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            try (InputStream in = connection.getInputStream()) {
                return in.read() > 0;
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    public void theMenuToggleEnablesRemoteAccess() throws Exception {
        assumeTrue(FixtureExperiment.available("values.phyphox"));
        Experiment activity = FixtureExperiment.launch("values.phyphox");
        try {
            assertTrue("the remote API answers although no switch is set and the menu toggle was "
                    + "not used", !remoteApiAnswers());

            UiObject2 overflow = device().wait(Until.findObject(By.desc("More options")), 5000);
            assertNotNull("the experiment menu is not reachable", overflow);
            overflow.click();

            UiObject2 remoteItem = device().wait(Until.findObject(By.text("Allow remote access")),
                    5000);
            assertNotNull("the menu has no remote access item", remoteItem);
            remoteItem.click();

            //The toggle explains itself before it does anything - that dialog is part of the
            //user path this suite protects.
            UiObject2 warning = device().wait(Until.findObject(By.text("Security warning!")), 5000);
            assertNotNull("the remote access toggle asked nothing before opening the server",
                    warning);
            device().findObject(By.text("OK")).click();

            long deadline = System.currentTimeMillis() + 10000;
            while (!remoteApiAnswers() && System.currentTimeMillis() < deadline)
                Thread.sleep(250);
            assertTrue("the menu toggle did not bring the remote server up", remoteApiAnswers());
        } finally {
            FixtureExperiment.close(activity);
        }
    }

    @Test
    public void theNetworkPrivacyNoticeIsInformational() throws Exception {
        assumeTrue(FixtureExperiment.available("http-error-down.phyphox"));
        Experiment activity = FixtureExperiment.launch("http-error-down.phyphox");
        try {
            UiObject2 notice = device().wait(Until.findObject(By.text("Privacy warning")), 10000);
            assertNotNull("opening a network experiment showed no privacy notice", notice);

            //Informational means exactly one way out. A decline control would mean the notice
            //gates something, which is a different contract than the one that was ruled.
            assertFalse("the privacy notice offers a decline - that is a finding, not a feature",
                    device().hasObject(By.text("Cancel")));

            UiObject2 ok = device().findObject(By.text("OK"));
            assertNotNull("the privacy notice has no OK", ok);
            ok.click();

            assertTrue("the notice stayed after its OK",
                    device().wait(Until.gone(By.text("Privacy warning")), 5000));
        } finally {
            FixtureExperiment.close(activity);
        }
    }

    @Test
    public void aDownloadedExperimentOffersToBeSaved() throws Exception {
        assumeTrue(FixtureExperiment.available("values.phyphox"));

        //Opened from a URL rather than from the collection, which is what makes the offer appear.
        FixtureExperiment.suppressHints();
        String url = "phyphox://" + FixtureExperiment.hostFromDevice() + "/values.phyphox";
        shell("am start -a android.intent.action.VIEW -d \"" + url + "\"");

        UiObject2 offer = device().wait(Until.findObject(By.textContains("experiment collection")),
                20000);
        assumeTrue("the fixture could not be served over http from the host", offer != null);

        //Declining leaves the experiment open and the collection untouched.
        device().findObject(By.text("Cancel")).click();
        assertTrue("the offer stayed after Cancel",
                device().wait(Until.gone(By.textContains("experiment collection")), 5000));
        assertTrue("the experiment did not open after declining the offer",
                device().wait(Until.hasObject(By.textContains("fixture")), 5000));
    }
}
