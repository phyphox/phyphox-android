package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// phyphox-test: lifecycle
//A running experiment under rotation, recreation, backgrounding, removal of its screen, a second experiment
//and quick start/stop, observed through the remote API, the run itself and the system's service and sensor records.
@RunWith(AndroidJUnit4.class)
public class LifecycleTest {

    private static final int PORT = 8080;
    private static final String FIXTURE = "values.phyphox";
    private static final String SENSOR_EXPERIMENT = "accelerometer.phyphox"; //The fixture uses no sensor

    private interface Check {
        boolean holds() throws Exception;
    }

    @Before
    public void enableRemoteApi() throws Exception {
        shell("setprop debug.phyphox.remote 1");
        shell("setprop debug.phyphox.remotePort " + PORT);
        setBackgroundMeasurement(false);
        //The foreground service's notification (Android 13+); the run continues without it, but the test should look like a real device
        if (Build.VERSION.SDK_INT >= 33)
            shell("pm grant " + getInstrumentation().getTargetContext().getPackageName() + " android.permission.POST_NOTIFICATIONS");
    }

    @After
    public void cleanUp() throws Exception {
        //A run may go on without any screen (see MeasurementRun): let it go, service included, before the next test
        getInstrumentation().runOnMainSync(() -> {
            MeasurementRun run = MeasurementRun.current();
            if (run != null)
                run.release();
        });
        for (Experiment activity : experimentActivities())
            FixtureExperiment.close(activity);
        setBackgroundMeasurement(false);
        device().setOrientationNatural();
        shell("setprop debug.phyphox.remote '\"\"'");
        shell("setprop debug.phyphox.remotePort '\"\"'");
    }

    //The "keep measuring in the background" setting, read when an experiment loads
    private void setBackgroundMeasurement(boolean on) {
        Context app = getInstrumentation().getTargetContext();
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
                .edit()
                .putBoolean("backgroundMeasurement", on)
                .commit();
    }

    //A timed run as the dialog would set it up
    private void setTimedRun(Experiment activity, double startDelay, double stopDelay) {
        getInstrumentation().runOnMainSync(() -> {
            activity.timedRun = true;
            activity.timedRunStartDelay = startDelay;
            activity.timedRunStopDelay = stopDelay;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T onMain(Callable<T> read) {
        final Object[] holder = new Object[1];
        getInstrumentation().runOnMainSync(() -> {
            try {
                holder[0] = read.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return (T) holder[0];
    }

    //The live experiment activities in whatever lifecycle stage, so their state can be read while they are stopped in
    //the background. Activities that earlier tests finished linger in the DESTROYED stage and must not be picked.
    private List<Experiment> experimentActivities() {
        return onMain(() -> {
            List<Experiment> found = new ArrayList<>();
            for (Stage stage : new Stage[]{Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED, Stage.STARTED, Stage.CREATED, Stage.PRE_ON_CREATE})
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))
                    if (activity instanceof Experiment && !found.contains(activity))
                        found.add((Experiment) activity);
            return found;
        });
    }

    //The run itself, which may be without a screen
    private boolean runMeasuring() {
        return onMain(() -> MeasurementRun.current() != null && MeasurementRun.current().measuring);
    }

    private boolean runEndedUnseen() {
        return onMain(() -> MeasurementRun.current() != null && MeasurementRun.current().isFinishedUnseen());
    }

    //The system's record of the service, not the app's own bookkeeping: the part of the dump about phyphox's
    //MeasurementService, or null. The dump also holds the "Last ANR service" of any app whatever the filter (a
    //SystemUI ANR on a slow first boot), so only a ServiceRecord of this very component counts.
    private String serviceRecord() throws Exception {
        String dump = device().executeShellCommand("dumpsys activity services MeasurementService");
        String component = new ComponentName(getInstrumentation().getTargetContext(), MeasurementService.class).flattenToShortString();
        Matcher own = Pattern.compile("ServiceRecord\\{\\p{XDigit}+ u\\d+ " + Pattern.quote(component) + "[ }]").matcher(dump);
        if (!own.find())
            return null;
        int next = dump.indexOf("ServiceRecord{", own.end());
        return dump.substring(own.start(), next < 0 ? dump.length() : next);
    }

    private boolean serviceRunning() throws Exception {
        return serviceRecord() != null;
    }

    private boolean serviceInForeground() throws Exception {
        String record = serviceRecord();
        return record != null && record.contains("isForeground=true");
    }

    private String notificationText() {
        NotificationManager manager = getInstrumentation().getTargetContext().getSystemService(NotificationManager.class);
        for (StatusBarNotification notification : manager.getActiveNotifications())
            if (notification.getId() == MeasurementService.NOTIFICATION_ID) {
                CharSequence text = notification.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
                return text != null ? text.toString() : null;
            }
        return null;
    }

    //Held now: the "Wake Locks" block of dumpsys power, not its log of earlier ones
    private boolean proximityLockHeld() throws Exception {
        String power = device().executeShellCommand("dumpsys power");
        int start = power.indexOf("Wake Locks: size=");
        if (start < 0)
            return false;
        int end = power.indexOf("\n\n", start);
        return power.substring(start, end < 0 ? power.length() : end).contains("'phyphox:measuring'");
    }

    //A sensor that phyphox listens to now: the dump lists each open connection by its listener's class, followed by a
    //line per sensor it has enabled. Its log of earlier registrations names the listener too, but not in this form.
    private boolean sensorsHeld() throws Exception {
        String dump = device().executeShellCommand("dumpsys sensorservice");
        return Pattern.compile("^\\t " + Pattern.quote(SensorInput.class.getName()) + " \\|.*\\n\\t .* 0x\\p{XDigit}{8} \\|",
                Pattern.MULTILINE).matcher(dump).find();
    }

    //What Recents trimming or a swipe does to the task of a screen
    private void removeTask(Experiment screen) {
        int taskId = screen.getTaskId();
        ActivityManager manager = getInstrumentation().getTargetContext().getSystemService(ActivityManager.class);
        for (ActivityManager.AppTask task : manager.getAppTasks())
            if (task.getTaskInfo().taskId == taskId)
                task.finishAndRemoveTask();
    }

    private Notification.Action notificationAction(int index) {
        NotificationManager manager = getInstrumentation().getTargetContext().getSystemService(NotificationManager.class);
        for (StatusBarNotification notification : manager.getActiveNotifications())
            if (notification.getId() == MeasurementService.NOTIFICATION_ID) {
                Notification.Action[] actions = notification.getNotification().actions;
                return actions != null && actions.length > index ? actions[index] : null;
            }
        return null;
    }

    private long notificationPostTime() {
        NotificationManager manager = getInstrumentation().getTargetContext().getSystemService(NotificationManager.class);
        for (StatusBarNotification notification : manager.getActiveNotifications())
            if (notification.getId() == MeasurementService.NOTIFICATION_ID)
                return notification.getPostTime();
        return -1;
    }

    private boolean notificationSaysEnded() {
        String ended = getInstrumentation().getTargetContext().getString(R.string.backgroundMeasurementEnded, "").trim();
        String text = notificationText();
        return text != null && text.startsWith(ended);
    }

    //What the notification does when tapped
    private void tapNotification() {
        Context app = getInstrumentation().getTargetContext();
        Intent intent = onMain(() -> MeasurementService.returnIntent(app, MeasurementRun.current()));
        app.startActivity(intent);
    }

    private boolean eventually(Check check) throws Exception {
        return eventually(check, 10000);
    }

    private boolean eventually(Check check, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!check.holds() && System.currentTimeMillis() < deadline)
            Thread.sleep(200);
        return check.holds();
    }

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    //UiDevice.pressHome waits for the screen to go idle, which a running countdown (the menu shows it) never does.
    //The key event returns at once, so this waits until the experiment screen has stopped.
    private void goHome() throws Exception {
        shell("input keyevent KEYCODE_HOME");
        assertTrue("the experiment screen did not leave", eventually(() -> onMain(() -> {
            for (Stage stage : new Stage[]{Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.RESTARTED})
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))
                    if (activity instanceof Experiment)
                        return false;
            return true;
        })));
    }

    private void shell(String command) throws Exception {
        device().executeShellCommand(command);
    }

    private JSONObject api(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://127.0.0.1:" + PORT + path).openConnection();
        connection.setRequestProperty("Connection", "close");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(8000); //cmd=start waits for the main thread's verdict (up to 5 s)
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

    //A remote start/stop is applied by the experiment's own loop on its next pass
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

    //Dark mode, language or font size: configuration changes the activity does not handle itself recreate it
    @Test
    public void aRunSurvivesTheRecreationOfItsScreen() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        for (boolean background : new boolean[]{false, true}) {
            String setting = background ? " (setting on)" : " (setting off)";
            setBackgroundMeasurement(background);
            Experiment first = FixtureExperiment.launch(FIXTURE);
            awaitApi();
            api("/control?cmd=start");
            awaitMeasuring(true);

            getInstrumentation().runOnMainSync(first::recreate);
            assertTrue("the screen was not recreated" + setting, eventually(() -> {
                Experiment current = FixtureExperiment.activity();
                return current != null && current != first;
            }));
            awaitApi();
            assertTrue("the measurement ended with the recreated screen" + setting, measuring());

            api("/control?cmd=stop");
            awaitMeasuring(false);
            FixtureExperiment.close(FixtureExperiment.activity());
            Thread.sleep(1500);
        }
    }

    @Test
    public void theBackgroundStopsTheRunCleanlyAndItCanResume() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        FixtureExperiment.launch(FIXTURE);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);

        goHome();
        Thread.sleep(3000);

        //Leaving the screen ends the measurement on purpose; pinned is that the app comes back, serves and runs again
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
    public void withTheSettingOnTheRunContinuesInTheBackground() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        setBackgroundMeasurement(true);
        FixtureExperiment.launch(FIXTURE);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);
        assertTrue("the foreground service did not start with the run", eventually(this::serviceInForeground));

        goHome();
        Thread.sleep(3000);
        //The remote server keeps serving a run that goes on in the background
        assertTrue("the measurement stopped in the background", measuring());
        assertTrue("the service is not in the foreground", serviceInForeground());

        FixtureExperiment.bringToForeground();
        Thread.sleep(2000);
        awaitApi();
        assertTrue("the measurement did not survive the return to the foreground", measuring());

        api("/control?cmd=stop");
        awaitMeasuring(false);
        assertTrue("the foreground service outlived the run", eventually(() -> !serviceRunning()));
    }

    @Test
    public void aRemoteStopInTheBackgroundLeavesTheResultForTheUser() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        setBackgroundMeasurement(true);
        FixtureExperiment.launch(FIXTURE);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);

        goHome();
        Thread.sleep(2000);
        api("/control?cmd=stop");
        assertTrue("the remote stop was not carried out in the background", eventually(() -> !runMeasuring()));

        //Nobody has seen the result: the service holds on to the process and the notification says why
        assertTrue("the ended run is not waiting for the user", runEndedUnseen());
        assertTrue("the service let go of the unseen result", serviceInForeground());
        assertTrue("the notification does not tell that the run ended: " + notificationText(), eventually(this::notificationSaysEnded));
        assertTrue("the remote server served on after the run ended in the background", eventually(() -> !apiAnswers()));

        FixtureExperiment.bringToForeground();
        awaitApi();
        assertTrue("the service outlived the return to the result", eventually(() -> !serviceRunning()));
        assertFalse("the run started again on the return", measuring());
    }

    @Test
    public void aTimedRunThatEndsInTheBackgroundWaitsForTheUser() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        setBackgroundMeasurement(true);
        Experiment activity = FixtureExperiment.launch(FIXTURE);
        awaitApi();
        setTimedRun(activity, 0, 5);
        api("/control?cmd=start");
        awaitMeasuring(true);

        goHome();
        assertTrue("the timed run did not end in the background", eventually(this::runEndedUnseen, 15000));
        assertTrue("the service let go of the unseen result", serviceInForeground());
        assertTrue("the notification does not tell that the run ended: " + notificationText(), eventually(this::notificationSaysEnded));

        FixtureExperiment.bringToForeground();
        awaitApi();
        assertTrue("the service outlived the return to the result", eventually(() -> !serviceRunning()));
    }

    @Test
    public void theCountdownOfATimedRunGoesOnInTheBackground() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        setBackgroundMeasurement(true);
        Experiment activity = FixtureExperiment.launch(FIXTURE);
        awaitApi();
        setTimedRun(activity, 3, 60);
        api("/control?cmd=start"); //Starts the countdown, and with it the service
        goHome();

        assertTrue("the countdown did not start the measurement in the background", eventually(this::runMeasuring, 15000));
        assertTrue("the service is not in the foreground", serviceInForeground());
        assertTrue("the measurement is not served in the background", measuring());
        api("/control?cmd=stop");
        assertTrue("the remote stop was not carried out in the background", eventually(() -> !runMeasuring()));
    }

    //Android kills the app when the service stops before its startForeground, which a zero-length run used to do
    @Test
    public void aZeroLengthTimedRunEndsWithoutACrash() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        setBackgroundMeasurement(true);
        Experiment activity = FixtureExperiment.launch(FIXTURE);
        awaitApi();
        setTimedRun(activity, 0, 0);
        api("/control?cmd=start");

        Thread.sleep(3000);
        assertTrue("the app stopped answering", apiAnswers());
        assertFalse("a zero-length run is still measuring", measuring());
        assertTrue("the service outlived the zero-length run", eventually(() -> !serviceRunning()));
    }

    //Recents trimming removes a task that sat long enough behind others, and a swipe looks the same to the app
    @Test
    public void aRunGoesOnWhenAndroidRemovesItsScreen() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        assumeTrue("TaskInfo.taskId", Build.VERSION.SDK_INT >= 29);
        setBackgroundMeasurement(true);
        Experiment first = FixtureExperiment.launch(FIXTURE);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);
        PhyphoxExperiment experiment = onMain(() -> first.experiment);

        goHome();
        Thread.sleep(1500);
        removeTask(first);
        assertTrue("the screen survived the removal of its task", eventually(() -> experimentActivities().isEmpty()));
        Thread.sleep(1000);
        assertTrue("the run ended with its screen", runMeasuring());
        assertTrue("the service ended with the screen", serviceInForeground());

        //The notification brings a new screen for the same run, data included
        tapNotification();
        Experiment second = FixtureExperiment.awaitLoaded();
        assertNotSame("no new screen", first, second);
        assertSame("the new screen loaded the experiment afresh instead of showing the run", experiment, onMain(() -> second.experiment));
        assertTrue("the new screen does not show the run as measuring", onMain(second::isMeasuring));
        awaitApi();
        assertTrue("the remote interface does not see the run", measuring());

        api("/control?cmd=stop");
        awaitMeasuring(false);
        assertTrue("the foreground service outlived the run", eventually(() -> !serviceRunning()));
    }

    //A screen that takes over a run must release its sensors when it stops it. Found on a phone: started, Home, the
    //task swiped away, back through the notification and stopped there, the accelerometer stayed on.
    @Test
    public void aRunStoppedFromTheScreenOfTheNotificationReleasesItsSensors() throws Exception {
        assumeTrue("TaskInfo.taskId", Build.VERSION.SDK_INT >= 29);
        setBackgroundMeasurement(true);
        Experiment first = FixtureExperiment.launchAsset(SENSOR_EXPERIMENT);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);
        assertTrue("the run listens to no sensor", eventually(this::sensorsHeld));

        goHome();
        Thread.sleep(1500);
        removeTask(first);
        assertTrue("the screen survived the removal of its task", eventually(() -> experimentActivities().isEmpty()));
        tapNotification();
        FixtureExperiment.awaitLoaded();
        awaitApi();

        api("/control?cmd=stop");
        awaitMeasuring(false);
        assertTrue("the sensors outlived the run", eventually(() -> !sensorsHeld()));
    }

    @Test
    public void aRunStoppedFromItsRecreatedScreenReleasesItsSensors() throws Exception {
        Experiment first = FixtureExperiment.launchAsset(SENSOR_EXPERIMENT);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);
        assertTrue("the run listens to no sensor", eventually(this::sensorsHeld));

        getInstrumentation().runOnMainSync(first::recreate);
        assertTrue("the screen was not recreated", eventually(() -> {
            Experiment current = FixtureExperiment.activity();
            return current != null && current != first;
        }));
        awaitApi();

        api("/control?cmd=stop");
        awaitMeasuring(false);
        assertTrue("the sensors outlived the run", eventually(() -> !sensorsHeld()));
    }

    @Test
    public void theNotificationReturnsToTheScreenOfTheRun() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        setBackgroundMeasurement(true);
        Experiment first = FixtureExperiment.launch(FIXTURE);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);

        goHome();
        Thread.sleep(1500);
        tapNotification();
        assertTrue("the screen of the run did not come back", eventually(() -> FixtureExperiment.activity() != null));
        assertSame("the notification opened another screen instead of returning", first, FixtureExperiment.activity());
        assertTrue("the run did not go on", measuring());

        api("/control?cmd=stop");
        awaitMeasuring(false);
    }

    //The notification's Stop ends the run but keeps the unseen result; its Close lets the process go
    @Test
    public void theNotificationStopsTheRunAndClosesItsResult() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        setBackgroundMeasurement(true);
        FixtureExperiment.launch(FIXTURE);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);
        goHome();
        assertTrue("no notification for the running measurement", eventually(() -> notificationAction(0) != null));

        notificationAction(0).actionIntent.send(); //Stop
        assertTrue("the notification's stop did not end the run", eventually(() -> !runMeasuring()));
        assertTrue("the ended run is not waiting for the user", runEndedUnseen());
        assertTrue("the notification does not tell that the run ended: " + notificationText(), eventually(this::notificationSaysEnded));

        notificationAction(0).actionIntent.send(); //Close
        assertTrue("the service outlived the closed result", eventually(() -> !serviceRunning()));
        assertTrue("the closed result is still held", onMain(() -> MeasurementRun.current() == null));
    }

    //Android 14+ lets the user swipe away the notification of a foreground service, which runs on without it
    @Test
    public void aSwipedAwayNotificationOfARunningMeasurementComesBack() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        assumeTrue("dismissable service notifications", Build.VERSION.SDK_INT >= 34);
        setBackgroundMeasurement(true);
        FixtureExperiment.launch(FIXTURE);
        awaitApi();
        api("/control?cmd=start");
        awaitMeasuring(true);
        goHome();
        assertTrue("no notification for the running measurement", eventually(() -> notificationText() != null));

        long posted = notificationPostTime();
        String running = getInstrumentation().getTargetContext().getString(R.string.backgroundMeasurementNotification, "").trim();
        device().openNotification();
        androidx.test.uiautomator.UiObject2 notification = device().wait(Until.findObject(By.textStartsWith(running)), 5000);
        assumeTrue("the notification is not in the shade", notification != null);
        notification.swipe(androidx.test.uiautomator.Direction.RIGHT, 1.0f);
        assertTrue("the notification was not swiped away and posted again", eventually(() -> notificationPostTime() > posted));
        device().pressBack();
        assertTrue("the swipe ended the run", runMeasuring());
    }

    //The proximity lock turns off the screen for whatever app is in front; it belongs to the experiment screen only
    @Test
    public void theProximityLockIsLeftBehindWithTheScreen() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        setBackgroundMeasurement(true);
        Context app = getInstrumentation().getTargetContext();
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(app).edit().putBoolean("proximityLock", true).commit();
        try {
            FixtureExperiment.launch(FIXTURE);
            awaitApi();
            api("/control?cmd=start");
            awaitMeasuring(true);
            assumeTrue("the device has no proximity lock", eventually(this::proximityLockHeld));

            goHome();
            assertTrue("the proximity lock stayed with the run in the background", eventually(() -> !proximityLockHeld()));
            assertTrue("the run did not go on", runMeasuring());

            FixtureExperiment.bringToForeground();
            assertTrue("the proximity lock did not come back with the screen", eventually(this::proximityLockHeld));
        } finally {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(app).edit().putBoolean("proximityLock", false).commit();
        }
    }

    //One run at a time goes on without its screen: a second experiment asks before it ends the first one's
    @Test
    public void aSecondExperimentAsksBeforeEndingTheRunInTheBackground() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        assumeTrue(FixtureExperiment.available("edits.phyphox"));
        shell("setprop debug.phyphox.remote '\"\"'"); //Two screens would compete for one port; the test asks the screens directly
        setBackgroundMeasurement(true);
        Experiment first = FixtureExperiment.launch(FIXTURE);
        assertTrue("the first experiment did not start", onMain(first::startMeasurement));
        goHome();
        Thread.sleep(1000);

        Experiment second = FixtureExperiment.launch("edits.phyphox");
        assertFalse("the second experiment started without asking", onMain(second::startMeasurement));
        assertTrue("the first run ended without asking", runMeasuring());

        androidx.test.uiautomator.UiObject2 start = device().wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)" + getInstrumentation().getTargetContext().getString(R.string.start)))), 5000);
        assertTrue("no question about the run in the background", start != null);
        start.click();
        assertTrue("the second experiment did not start after the answer", eventually(() -> onMain(second::isMeasuring)));
        assertTrue("the first run was not let go", onMain(() -> MeasurementRun.current() != null && MeasurementRun.current().experiment == second.experiment));
    }

    @Test
    public void aSecondExperimentReplacesTheFirst() throws Exception {
        assumeTrue(FixtureExperiment.available(FIXTURE));
        assumeTrue(FixtureExperiment.available("edits.phyphox"));
        FixtureExperiment.launch(FIXTURE);
        awaitApi();
        assertTrue("the first experiment is not the one being served",
                api("/config").getString("title").contains("value"));

        //The first experiment is closed before the second opens: two alive at once means two servers on one port
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
