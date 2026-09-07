package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

//The BLE compatibility suite drives a transferred experiment over the remote API, which only works
//because Experiment.onExperimentLoaded applies debug.phyphox.remote to every route, Bluetooth included.
//Separate from BleCompatConnectTest: that one runs under the driver's own switch handling.
@RunWith(AndroidJUnit4.class)
public class BluetoothRemoteSeamTest {

    private static final int PORT = 8080;

    @Before
    public void quietFirstRunDialogs() {
        FixtureExperiment.suppressHints();
    }

    @Test
    public void aBluetoothExperimentServesTheRemoteApiWhenTheSwitchIsSet() throws Exception {
        shell("setprop debug.phyphox.remote 1");
        shell("setprop debug.phyphox.remotePort " + PORT);
        try {
            FixtureExperiment.launchAssetWithoutWaiting("bluetooth/Heart Rate.phyphox");
            //No device picked: the API has to answer in the state the host finds a transferred experiment in
            long deadline = System.currentTimeMillis() + 30000;
            boolean answered = false;
            while (!answered && System.currentTimeMillis() < deadline) {
                answered = remoteApiAnswers();
                if (!answered)
                    Thread.sleep(500);
            }
            assertTrue("the remote API did not come up for a Bluetooth experiment although "
                    + "debug.phyphox.remote is set - the host cannot reach a device-delivered "
                    + "experiment either", answered);
        } finally {
            shell("setprop debug.phyphox.remote '\"\"'");
            shell("setprop debug.phyphox.remotePort '\"\"'");
            FixtureExperiment.close(FixtureExperiment.activity());
        }
    }

    private void shell(String command) throws Exception {
        UiDevice.getInstance(getInstrumentation()).executeShellCommand(command);
    }

    private boolean remoteApiAnswers() {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                    new java.net.URL("http://127.0.0.1:" + PORT + "/config").openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            try (java.io.InputStream in = connection.getInputStream()) {
                return in.read() > 0;
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
