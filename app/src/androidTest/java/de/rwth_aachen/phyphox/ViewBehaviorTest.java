package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

// phyphox-test: view-behavior
//What the interactive view elements do to their buffers: type into every edit variant, press
//every button, flip the toggles, move both slider types, pick from the dropdown - and read the
//result back through the remote API, which is the bus the fixtures are wired for (every element
//of phyphox-docs' view fixtures writes to an observable buffer).
//
//Reading through the API rather than off the experiment object is deliberate: it proves the
//value actually landed in the buffer the rest of the app and the web interface see.
//
//The taps come from UiAutomator rather than Espresso. An open experiment redraws continuously,
//so its main looper never idles and every Espresso interaction ends in AppNotIdleException after
//a minute of waiting; UiAutomator drives the same touch events without that requirement.
@RunWith(AndroidJUnit4.class)
public class ViewBehaviorTest {

    private static final int PORT = 8080;

    @Before
    public void enableRemoteApi() throws Exception {
        //The remote API is switched on the way the lab driver does it - a shell-only property,
        //read when the experiment launches (see DebugSwitches).
        shell("setprop debug.phyphox.remote 1");
        shell("setprop debug.phyphox.remotePort " + PORT);
    }

    @After
    public void disableRemoteApi() throws Exception {
        shell("setprop debug.phyphox.remote '\"\"'");
        shell("setprop debug.phyphox.remotePort '\"\"'");
    }

    private void shell(String command) throws Exception {
        UiDevice.getInstance(getInstrumentation()).executeShellCommand(command);
    }

    // ----------------------------------------------------------------- the bus

    //The contents of one buffer, read through GET /get on the device itself.
    private double[] buffer(String name) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://127.0.0.1:" + PORT + "/get?" + name + "=full").openConnection();
        connection.setRequestProperty("Connection", "close");
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1)
                out.write(chunk, 0, n);
            JSONArray values = new JSONObject(out.toString("UTF-8"))
                    .getJSONObject("buffer").getJSONObject(name).getJSONArray("buffer");
            double[] result = new double[values.length()];
            for (int i = 0; i < result.length; i++)
                result[i] = values.isNull(i) ? Double.NaN : values.getDouble(i);
            return result;
        } finally {
            connection.disconnect();
        }
    }

    //A user input reaches its buffer through the main loop, not synchronously with the tap.
    private double[] awaitBuffer(String name, double expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        double[] values = buffer(name);
        while (System.currentTimeMillis() < deadline
                && !(values.length > 0 && Math.abs(values[values.length - 1] - expected) < 1e-6)) {
            Thread.sleep(100);
            values = buffer(name);
        }
        return values;
    }

    private void assertBuffer(String name, double expected) throws Exception {
        double[] values = awaitBuffer(name, expected);
        assertTrue("buffer " + name + " is empty", values.length > 0);
        assertEquals("buffer " + name, expected, values[values.length - 1], 1e-6);
    }

    // -------------------------------------------------------------- the views

    //The view of one element, found through the experiment itself rather than by matching text:
    //the fixtures name their elements, and that name is what the assertions talk about.
    //
    //Waits, because FixtureExperiment.awaitLoaded() returns once the experiment has PARSED and
    //the views are built later, when the pager inflates its first page. Looking only once
    //therefore raced the layout and failed as "No View for element ..." on a busy emulator -
    //a t1 failure on 2026-08-28 that had nothing to do with the element it named. Every other
    //helper here polls to a deadline; so does this one.
    private <T extends View> T viewOf(Experiment activity, String label, Class<T> type)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            T found = findViewOf(activity, label, type);
            if (found != null)
                return found;
            if (System.currentTimeMillis() >= deadline)
                break;
            Thread.sleep(100);
        }
        //Name what IS on screen, so a label that never appears is told apart from one that was
        //simply not there yet - the message alone could not distinguish them.
        throw new AssertionError("No " + type.getSimpleName() + " for element \"" + label
                + "\" after 5 s. Elements with a view: " + laidOutLabels(activity));
    }

    private <T extends View> T findViewOf(Experiment activity, String label, Class<T> type) {
        for (ExpView view : activity.experiment.experimentViews)
            for (ExpView.expViewElement element : view.elements)
                if (label.equals(element.label) && element.rootView != null) {
                    T found = descendant(element.rootView, type);
                    if (found != null)
                        return found;
                }
        return null;
    }

    private String laidOutLabels(Experiment activity) {
        StringBuilder labels = new StringBuilder();
        for (ExpView view : activity.experiment.experimentViews)
            for (ExpView.expViewElement element : view.elements)
                if (element.rootView != null)
                    labels.append(labels.length() == 0 ? "" : ", ").append(element.label);
        return labels.length() == 0 ? "(none - no page has been laid out)" : labels.toString();
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T descendant(View view, Class<T> type) {
        if (type.isInstance(view))
            return (T) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = descendant(group.getChildAt(i), type);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    //The elements commit an edit when it loses focus, which is what tapping elsewhere does.
    private void commit(EditText edit) throws Exception {
        getInstrumentation().runOnMainSync(edit::clearFocus);
        Thread.sleep(300);
    }

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    //Brings a view on screen and taps it at a fraction of its width - the whole width for a
    //button, three quarters along for a slider.
    private void tapAt(View view, float fraction) throws Exception {
        getInstrumentation().runOnMainSync(() ->
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()),
                        true));
        Thread.sleep(400);

        final int[] point = new int[2];
        getInstrumentation().runOnMainSync(() -> {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            point[0] = location[0] + (int) (view.getWidth() * fraction);
            point[1] = location[1] + view.getHeight() / 2;
        });
        device().click(point[0], point[1]);
        Thread.sleep(400);
    }

    private void tap(View view) throws Exception {
        tapAt(view, 0.5f);
    }

    //Drags along a view, from one fraction of its width to another - how a slider thumb is
    //actually moved. A single tap works too, but a busy emulator drops it often enough to make a
    //suite flaky, while a drag is followed all the way.
    private void dragWithin(View view, float from, float to) throws Exception {
        getInstrumentation().runOnMainSync(() ->
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()),
                        true));
        Thread.sleep(400);

        final int[] points = new int[3];
        getInstrumentation().runOnMainSync(() -> {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            points[0] = location[0] + (int) (view.getWidth() * from);
            points[1] = location[0] + (int) (view.getWidth() * to);
            points[2] = location[1] + view.getHeight() / 2;
        });
        device().swipe(points[0], points[2], points[1], points[2], 20);
        Thread.sleep(600);
    }

    //Types with real key events, so the fields' input restrictions apply exactly as they do
    //under a user's fingers.
    private void type(EditText edit, String text) throws Exception {
        getInstrumentation().runOnMainSync(() -> edit.setText(""));
        tap(edit);
        getInstrumentation().sendStringSync(text);
        Thread.sleep(200);
    }

    // --------------------------------------------------------------- the tests

    @Test
    public void editsWriteWhatTheyAccept() throws Exception {
        assumeTrue(FixtureExperiment.available("edits.phyphox"));
        Experiment activity = FixtureExperiment.launch("edits.phyphox");
        try {
            EditText plain = viewOf(activity, "plain", EditText.class);
            type(plain, "2.75");
            commit(plain);
            assertBuffer("plain", 2.75);

            //Out of range is clamped to the bound, not rejected.
            EditText bounded = viewOf(activity, "bounded", EditText.class);
            type(bounded, "99");
            commit(bounded);
            assertBuffer("bounded", 10);

            //An unsigned field never yields a negative value: typing the minus is refused by the
            //key listener, and a negative that slips through in another notation is folded.
            EditText unsigned = viewOf(activity, "unsigned", EditText.class);
            type(unsigned, "-4");
            commit(unsigned);
            assertBuffer("unsigned", 4);

            //An integer-only field refuses the decimal separator, so "3.5" arrives as 35.
            EditText integer = viewOf(activity, "integer only", EditText.class);
            type(integer, "3.5");
            commit(integer);
            assertBuffer("integer", 35);

            //The field shows a value in cm while the buffer keeps metres: entering 42 stores 0.42.
            EditText scaled = viewOf(activity, "unit and factor", EditText.class);
            type(scaled, "42");
            commit(scaled);
            assertBuffer("scaled", 0.42);
        } finally {
            FixtureExperiment.close(activity);
        }
    }

    @Test
    public void buttonsWriteTheirBuffers() throws Exception {
        assumeTrue(FixtureExperiment.available("buttons-toggles.phyphox"));
        Experiment activity = FixtureExperiment.launch("buttons-toggles.phyphox");
        try {
            tap(viewOf(activity, "write 7", View.class));
            assertBuffer("target", 7);

            //Two input/output pairs on one buffer: each clears its output first, so the last
            //write is what remains - never both values.
            tap(viewOf(activity, "two writes, last wins", View.class));
            double[] log = awaitBuffer("log", 2);
            assertArrayEquals("the second write replaces the first", new double[]{2}, log, 1e-6);

            tap(viewOf(activity, "clear", View.class));
            long deadline = System.currentTimeMillis() + 5000;
            while (buffer("log").length > 0 && System.currentTimeMillis() < deadline)
                Thread.sleep(100);
            assertEquals("an empty input clears the buffer", 0, buffer("log").length);
        } finally {
            FixtureExperiment.close(activity);
        }
    }

    @Test
    public void togglesWriteTheirBuffers() throws Exception {
        assumeTrue(FixtureExperiment.available("buttons-toggles.phyphox"));
        Experiment activity = FixtureExperiment.launch("buttons-toggles.phyphox");
        try {
            tap(viewOf(activity, "on by default", View.class));
            assertBuffer("switch1", 0);

            tap(viewOf(activity, "off", View.class));
            assertBuffer("switch2", 1);
        } finally {
            FixtureExperiment.close(activity);
        }
    }

    @Test
    public void slidersWriteTheirBuffers() throws Exception {
        assumeTrue(FixtureExperiment.available("sliders-dropdowns.phyphox"));
        Experiment activity = FixtureExperiment.launch("sliders-dropdowns.phyphox");
        try {
            //Where exactly a tap lands is not the point - that it moves the value onto the step
            //grid, inside the range, and away from where it started is.
            //The plain slider sits at 2.5 of 0..5, so the thumb is in the middle; drag it right.
            View plain = viewOf(activity, "plain",
                    com.google.android.material.slider.Slider.class);
            dragWithin(plain, 0.5f, 0.8f);
            double[] s1 = buffer("s1");
            if (s1.length > 0 && Math.abs(s1[0] - 2.5) < 1e-6) {
                //A dropped touch on a loaded emulator is not a finding about the slider.
                dragWithin(plain, 0.5f, 0.8f);
                s1 = buffer("s1");
            }
            assertTrue("the plain slider did not move: " + Arrays.toString(s1),
                    s1.length > 0 && Math.abs(s1[0] - 2.5) > 1e-6);
            assertTrue("value outside the range: " + s1[0], s1[0] >= 0 && s1[0] <= 5);
            assertTrue("value off the 0.1 step grid: " + s1[0],
                    Math.abs(s1[0] * 10 - Math.round(s1[0] * 10)) < 1e-6);

            //The range slider holds 20 - 60 of 0..100: drag the upper thumb towards the end.
            View range = viewOf(activity, "range",
                    com.google.android.material.slider.RangeSlider.class);
            dragWithin(range, 0.6f, 0.95f);
            double lower = buffer("lower")[0];
            double upper = buffer("upper")[0];
            if (Math.abs(upper - 60) < 1e-6 && Math.abs(lower - 20) < 1e-6) {
                dragWithin(range, 0.6f, 0.95f);
                lower = buffer("lower")[0];
                upper = buffer("upper")[0];
            }
            assertTrue("the range slider did not move: " + lower + " - " + upper,
                    Math.abs(upper - 60) > 1e-6 || Math.abs(lower - 20) > 1e-6);
            assertTrue("the thumbs crossed: " + lower + " - " + upper, lower <= upper);
            assertTrue("range outside its bounds", lower >= 0 && upper <= 100);
        } finally {
            FixtureExperiment.close(activity);
        }
    }

    @Test
    public void theDropdownWritesItsBuffer() throws Exception {
        assumeTrue(FixtureExperiment.available("sliders-dropdowns.phyphox"));
        Experiment activity = FixtureExperiment.launch("sliders-dropdowns.phyphox");
        try {
            //The dropdown is Material's exposed menu - a TextInputLayout over an
            //AutoCompleteTextView, not a Spinner - so picking means opening the list and
            //tapping an entry, in its own popup window.
            AutoCompleteTextView dropdown = viewOf(activity, "mode", AutoCompleteTextView.class);
            tap(dropdown);

            UiObject2 entry = device().wait(Until.findObject(By.text("fast")), 5000);
            assertTrue("the dropdown did not open its list", entry != null);
            entry.click();

            assertBuffer("choice", 2);
        } finally {
            FixtureExperiment.close(activity);
        }
    }

}
