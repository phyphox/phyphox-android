package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// phyphox-test: lifecycle
//What happens to a running experiment when the system moves it around: rotation mid-run, going
//to the background and coming back, a second experiment opened over it, and starts and stops in
//quick succession. The remote API is the observer - it reports whether the measurement is
//running and how much data there is, which is what "still running" has to mean.
@RunWith(AndroidJUnit4.class)
public class LifecycleTest {

    private static final int PORT = 8080;
    private static final String FIXTURE = "values.phyphox";

    @Before
    public void enableRemoteApi() throws Exception {
        shell("setprop debug.phyphox.remote 1");
        shell("setprop debug.phyphox.remotePort " + PORT);
    }

    @After
    public void cleanUp() throws Exception {
        FixtureExperiment.close(FixtureExperiment.activity());
        device().setOrientationNatural();
        shell("setprop debug.phyphox.remote '\"\"'");
        shell("setprop debug.phyphox.remotePort '\"\"'");
    }

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    private void shell(String command) throws Exception {
        device().executeShellCommand(command);
    }

    private JSONObject api(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://127.0.0.1:" + PORT + path).openConnection();
        connection.setRequestProperty("Connection", "close");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1)
                out.write(chunk, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            connection.disconnect();
        }
    }

    private boolean measuring() throws Exception {
        return api("/get?").getJSONObject("status").getBoolean("measuring");
    }

    private boolean apiAnswers() {
        try {
            api("/config");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    //A start or stop asked for over the API is handed to the experiment's own loop, which
    //applies it on its next pass - so the answer to "is it measuring" follows a moment later.
    private void awaitMeasuring(boolean expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (measuring() != expected && System.currentTimeMillis() < deadline)
            Thread.sleep(200);
        assertEquals(expected ? "the experiment did not start" : "the experiment did not stop",
                expected, measuring());
    }

    private void awaitApi() throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (!apiAnswers() && System.currentTimeMillis() < deadline)
            Thread.sleep(250);
        assertTrue("the remote API never came up", apiAnswers());
    }

    @Test
    public void aRunSurvivesRotation() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        FixtureExperiment.launch(FIXTURE);
        awaitApi();

        api("/control?cmd=start");
        awaitMeasuring(true);

        device().setOrientationLeft();
        Thread.sleep(3000);
        awaitApi();
        assertTrue("the measurement stopped when the screen rotated", measuring());

        device().setOrientationNatural();
        Thread.sleep(3000);
        awaitApi();
        assertTrue("the measurement stopped when the screen rotated back", measuring());

        api("/control?cmd=stop");
        awaitMeasuring(false);
    }

    @Test
    public void theBackgroundStopsTheRunCleanlyAndItCanResume() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        FixtureExperiment.launch(FIXTURE);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);

        device().pressHome();
        Thread.sleep(3000);

        //Leaving the screen ends the measurement on purpose: phyphox shuts its inputs down when
        //the experiment is no longer in front, and the remote server goes with them. What this
        //pins is that it happens cleanly - the app comes back, serves again, and runs again.
        FixtureExperiment.bringToForeground();
        Thread.sleep(2000);
        awaitApi();
        assertEquals("the measurement kept running in the background", false, measuring());

        api("/control?cmd=start");
        awaitMeasuring(true);
        api("/control?cmd=stop");
        awaitMeasuring(false);
    }

    @Test
    public void aSecondExperimentReplacesTheFirst() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        assumeTrue(FixtureExperiment.available("edits.phyphox"));
        FixtureExperiment.launch(FIXTURE);
        awaitApi();
        assertTrue("the first experiment is not the one being served",
                api("/config").getString("title").contains("value"));

        //The first experiment is closed before the second opens. Two experiment screens alive at
        //once means two remote servers competing for one port, which is a different question
        //(and the reason the T1 sweep restarts the app between experiments).
        FixtureExperiment.close(FixtureExperiment.activity());
        Thread.sleep(1500);

        Experiment second = FixtureExperiment.launch("edits.phyphox");
        awaitApi();
        assertTrue("the second experiment did not take over: " + api("/config").getString("title"),
                api("/config").getString("title").contains("edit"));
        assertTrue("the second experiment is not loaded", second.experiment.loaded);

        //And the screen shows it, not a leftover of the first.
        assertTrue("the experiment screen did not follow",
                device().wait(Until.hasObject(By.textContains("plain")), 5000));
    }

    @Test
    public void startAndStopInQuickSuccessionLeaveAConsistentState() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        FixtureExperiment.launch(FIXTURE);
        awaitApi();

        for (int i = 0; i < 5; i++) {
            api("/control?cmd=start");
            api("/control?cmd=stop");
        }

        awaitMeasuring(false);
        assertTrue("the app stopped answering after rapid start/stop", apiAnswers());

        //And it can still be started afterwards.
        api("/control?cmd=start");
        awaitMeasuring(true);
        api("/control?cmd=stop");
        awaitMeasuring(false);
    }
}
