package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

//The precondition the BLE compatibility suite rests on, and the half of it that needs no board.
//
//That suite drives the phone from the host over the remote API (phyphox-docs/tools/lab/ble.py):
//an instrumented test picks the device out of the scan, and everything after that is asserted
//from outside. Which only works if an experiment that arrives from a Bluetooth device serves the
//remote API when debug.phyphox.remote is set.
//
//It does, and not by a special case: the switch is applied in Experiment.onExperimentLoaded,
//where EVERY experiment finishes loading, and every Bluetooth route ends up there through
//ExperimentListActivity - BluetoothExperimentLoader's success callback and the zip handler both
//just start the Experiment activity. So a delivered experiment gets it exactly as a launched one
//does, and there was nothing to extend.
//
//But that is a seam nobody would think to preserve while refactoring, and if it breaks the whole
//lab suite reports a phone it cannot reach rather than a bug it found. Hence this test: it is
//cheap, it needs no hardware, and it fails with a message naming that consequence.
//
//Separate class from BleCompatConnectTest on purpose. That class is what the driver runs by name
//while it owns the switch itself, and this one sets and clears the switch around its own run -
//in the same class the clearing would race the driver's assertions.
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
            //It stops at "please pick a device" without one, which is exactly the state the host
            //finds a transferred experiment in before it starts it - and the API has to answer
            //there, not only once something is connected.
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
