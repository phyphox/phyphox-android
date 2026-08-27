package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// phyphox-test: ble-compat-arduino
// phyphox-test: ble-compat-micropython
//The UI half of the BLE compatibility suite, and deliberately only that half: picking a device
//out of a scan has no equivalent over the remote API, so it has to be driven here, while
//everything that follows - the values, the rates, what the board actually emits - is asserted
//from the host against the shared expectations (phyphox-docs/tools/lab/ble.py and
//fixtures/ble/scenarios.yml). Duplicating those assertions in two languages would let them
//drift; this keeps them in one file and still exercises the real scan UI, which is part of what
//the suite protects.
//
//So this test ends where the host takes over: the experiment the device offers is loaded, not
//started, and serving the remote API. It asserts nothing about the data.
//
//The device name comes from the driver, which flashes the library examples UNMODIFIED and knows
//what they advertise as:
//
//    adb shell am instrument -e class de.rwth_aachen.phyphox.BleCompatTest \
//        -e bleDevice phyphox-arduino ... 
//
//Without that parameter there is no board to talk to and that test skips itself, which is what
//happens in CI - the row needs hardware and runs in the lab. The seam check below needs none and
//runs everywhere, because a suite that cannot reach the phone is worth catching before the board
//is even involved.
@RunWith(AndroidJUnit4.class)
public class BleCompatTest {

    private static final String PACKAGE = "de.rwth_aachen.phyphox";
    private static final int PORT = 8080;

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    private UiObject2 waitForId(String id, long timeout) {
        return device().wait(Until.findObject(By.res(PACKAGE + ":id/" + id)), timeout);
    }

    @Before
    public void quietFirstRunDialogs() {
        FixtureExperiment.suppressHints();
    }

    //The precondition the whole suite rests on, and the one half of it that needs no board: an
    //experiment with a Bluetooth block serves the remote API when the switch is set, so the host
    //can reach it. The switch is applied where every experiment finishes loading
    //(Experiment.onExperimentLoaded), which is the same place a transferred one arrives at, so
    //this covers the delivered case too as far as it can be covered without hardware.
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
        device().executeShellCommand(command);
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

    @Test
    public void theDeviceOffersItsExperimentAndItLoads() throws Exception {
        String name = InstrumentationRegistry.getArguments().getString("bleDevice");
        assumeTrue("no bleDevice given - this row needs a board and runs in the lab",
                name != null && !name.trim().isEmpty());
        name = name.trim();

        //The collection, where the scan lives.
        Context app = getInstrumentation().getTargetContext();
        Intent intent = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        app.startActivity(intent);

        //By resource id, not by label: the labels are translated and the run may be in any
        //language, but the ids are the same in every build.
        UiObject2 fab = waitForId("newExperiment", 20000);
        assertNotNull("the collection did not come up with its new-experiment button", fab);
        fab.click();
        UiObject2 bluetooth = waitForId("newExperimentBluetooth", 10000);
        assertNotNull("the new-experiment menu has no Bluetooth entry", bluetooth);
        bluetooth.click();

        //The scan needs a moment to find anything, and there is deliberately more than one board
        //advertising: taking the first entry would pass against the wrong device, so this waits
        //for the name the driver asked for and for nothing else.
        UiObject2 entry = device().wait(Until.findObject(By.text(name)), 45000);
        assertNotNull("the scan did not list a device called \"" + name + "\" within 45 s - is it "
                + "powered and advertising?", entry);
        try {
            entry.click();
        } catch (StaleObjectException e) {
            //The scan list rebuilds as devices come and go; look the entry up once more.
            entry = device().wait(Until.findObject(By.text(name)), 10000);
            assertNotNull("\"" + name + "\" disappeared from the scan before it could be picked",
                    entry);
            entry.click();
        }

        //A device that both offers its own experiment and matches bundled ones asks which to use.
        //A board running an unmodified library example offers only its own, and phyphox opens it
        //without asking - so this dialog is optional, and only its "load from device" choice is
        //the one under test.
        UiObject2 loadFromDevice = device().wait(Until.findObject(
                By.text(app.getString(R.string.newExperimentBluetoothLoadFromDevice))), 5000);
        if (loadFromDevice != null)
            loadFromDevice.click();

        //The transfer runs over BLE and is slow on purpose - the experiment is chunked over a
        //characteristic - so this is the one wait worth being generous about.
        Experiment experiment = awaitLoaded(90000);
        assertNotNull("the experiment the device offers did not load within 90 s", experiment);
        assertTrue("the experiment arrived but did not parse: " + experiment.experiment.message,
                experiment.experiment.loaded);

        //Left loaded and not started: the host starts it over the remote API and does the
        //measuring, because that is where the shared expectations live.
        assertFalse("the experiment must be left for the host to start, not started here",
                experiment.measuring);
    }

    //FixtureExperiment.awaitLoaded has a fixed deadline and throws; the BLE transfer needs its
    //own, and a null lets the assertion above say what actually went wrong.
    private Experiment awaitLoaded(long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            Experiment activity = FixtureExperiment.activity();
            if (activity != null && activity.experiment != null && activity.experiment.loaded)
                return activity;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }
}
