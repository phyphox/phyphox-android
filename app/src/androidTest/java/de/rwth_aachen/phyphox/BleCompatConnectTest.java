package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

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
//UI half of the BLE compatibility suite: picks the device out of the scan and loads its
//experiment without starting it; the host (phyphox-docs/tools/lab/ble.py) starts and measures it
//over the remote API. Class name, bleDevice, holdForHost and debug.phyphox.remote are its contract.
@RunWith(AndroidJUnit4.class)
public class BleCompatConnectTest {

    private static final String PACKAGE = "de.rwth_aachen.phyphox";
    private static final String RELEASE_PROPERTY = "debug.phyphox.labRelease";
    //Only has to outlast the host's measurement; keeps a crashed host from wedging the phone.
    private static final long HOLD_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final String TAG = "phyphoxBleCompat";

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    private UiObject2 waitForId(String id, long timeout) {
        return device().wait(Until.findObject(By.res(PACKAGE + ":id/" + id)), timeout);
    }

    @Before
    public void quietFirstRunDialogs() {
        FixtureExperiment.suppressHints();
        //autoConfirm does not answer system permission dialogs and the driver does not grant these.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            grant("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN");
        grant("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION");
    }

    //Via the shell: UiAutomation.grantRuntimePermission throws NoSuchMethodError below API 28.
    private void grant(String... permissions) {
        for (String permission : permissions) {
            try {
                shell("pm grant " + PACKAGE + " " + permission);
            } catch (Exception e) {
                //unknown to this API level, or already granted
            }
        }
    }

    @Test
    public void theDeviceOffersItsExperimentAndItLoads() throws Exception {
        String name = InstrumentationRegistry.getArguments().getString("bleDevice");
        assumeTrue("no bleDevice given - this row needs a board and runs in the lab",
                name != null && !name.trim().isEmpty());
        name = name.trim();
        boolean holdForHost = "true".equalsIgnoreCase(argument("holdForHost"));

        //Before the app starts: the host may release this test while the assertions below still run.
        if (holdForHost)
            shell("setprop " + RELEASE_PROPERTY + " 0");

        Context app = getInstrumentation().getTargetContext();
        Intent intent = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        app.startActivity(intent);

        UiObject2 fab = waitForId("newExperiment", 20000);
        assertNotNull("the collection did not come up with its new-experiment button", fab);
        fab.click();
        UiObject2 bluetooth = waitForId("newExperimentBluetooth", 10000);
        assertNotNull("the new-experiment menu has no Bluetooth entry", bluetooth);
        bluetooth.click();

        //Several boards advertise, so wait for the requested name only. The scan dialog never goes
        //idle (the list rebuilds continuously): every lookup pays the idle timeout, entries go stale.
        UiObject2 entry = device().wait(Until.findObject(By.text(name)), 45000);
        assertNotNull("the scan did not list a device called \"" + name + "\" within 45 s - is it "
                + "powered and advertising?", entry);
        try {
            entry.click();
        } catch (StaleObjectException e) {
            entry = device().wait(Until.findObject(By.text(name)), 10000);
            assertNotNull("\"" + name + "\" disappeared from the scan before it could be picked",
                    entry);
            entry.click();
        }

        //Only asked when the device also matches a bundled experiment; unmodified examples do not.
        UiObject2 loadFromDevice = device().wait(Until.findObject(
                By.text(app.getString(R.string.newExperimentBluetoothLoadFromDevice))), 5000);
        if (loadFromDevice != null)
            loadFromDevice.click();

        Experiment experiment = awaitLoaded(90000);
        assertNotNull("the experiment the device offers did not load within 90 s", experiment);
        assertTrue("the experiment arrived but did not parse: " + experiment.experiment.message,
                experiment.experiment.loaded);

        assertFalse("the experiment must be left for the host to start, not started here",
                experiment.measuring);

        //Instrumentation runs in the app's process: returning kills the remote server the host needs.
        if (holdForHost)
            holdUntilTheHostIsDone();
    }

    //Parks until the driver sets the release property; a host that died halfway fails the run.
    private void holdUntilTheHostIsDone() throws Exception {
        Log.i(TAG, "loaded and holding the app open for the host; release with: adb shell setprop "
                + RELEASE_PROPERTY + " 1");
        long deadline = System.currentTimeMillis() + HOLD_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if ("1".equals(shell("getprop " + RELEASE_PROPERTY).trim())) {
                Log.i(TAG, "released by the host");
                return;
            }
            Thread.sleep(500);
        }
        fail("the host never released this test within " + (HOLD_TIMEOUT_MS / 1000) + " s - it "
                + "is measuring against the app this test is holding open, so either it never got "
                + "that far or it failed without setting " + RELEASE_PROPERTY);
    }

    private String argument(String name) {
        return InstrumentationRegistry.getArguments().getString(name);
    }

    private String shell(String command) throws Exception {
        return device().executeShellCommand(command);
    }

    //FixtureExperiment.awaitLoaded throws on a fixed deadline; the BLE transfer needs its own.
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
