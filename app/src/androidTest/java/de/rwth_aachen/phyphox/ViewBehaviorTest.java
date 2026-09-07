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

import androidx.viewpager.widget.ViewPager;

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
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

// phyphox-test: view-behavior
//What the interactive view elements do to their buffers, read back through the remote API.
//UiAutomator, not Espresso: an open experiment redraws continuously and never idles.
@RunWith(AndroidJUnit4.class)
public class ViewBehaviorTest {

    private static final int PORT = 8080;

    @Before
    public void enableRemoteApi() throws Exception {
        //shell-only property, read when the experiment launches (see DebugSwitches)
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

    //A refused connection counts as "not yet": the server starts after launch() has returned
    private double[] awaitBuffer(String name, double expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            try {
                double[] values = buffer(name);
                if (values.length > 0 && Math.abs(values[values.length - 1] - expected) < 1e-6
                        || System.currentTimeMillis() >= deadline)
                    return values;
            } catch (ConnectException e) {
                if (System.currentTimeMillis() >= deadline)
                    throw e;
            }
            Thread.sleep(100);
        }
    }

    private void assertBuffer(String name, double expected) throws Exception {
        double[] values = awaitBuffer(name, expected);
        assertTrue("buffer " + name + " is empty", values.length > 0);
        assertEquals("buffer " + name, expected, values[values.length - 1], 1e-6);
    }

    // -------------------------------------------------------------- the views

    //Polls: awaitLoaded() returns once parsed, and the views are built later when the pager inflates.
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

    //A drag is followed all the way; a busy emulator drops single taps often enough to be flaky.
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

    //Real key events, so the fields' input restrictions apply.
    private void type(EditText edit, String text) throws Exception {
        getInstrumentation().runOnMainSync(() -> edit.setText(""));
        tap(edit);
        getInstrumentation().sendStringSync(text);
        Thread.sleep(200);
    }

    // --------------------------------------------------------------- the tests

    //Swaps the page and lets it settle; no waitForIdleSync(), the main looper never idles
    private void page(Experiment activity, int index) throws Exception {
        activity.runOnUiThread(() ->
                ((ViewPager) activity.findViewById(R.id.view_pager)).setCurrentItem(index, false));
        Thread.sleep(1500);
    }

    //A default fills an EMPTY buffer and never one the container already holds (spec, default attribute).
    @Test
    public void containerInitBeatsAControlsDefault() throws Exception {
        assumeTrue(FixtureExperiment.available("init-vs-default.phyphox"));
        Experiment activity = FixtureExperiment.launch("init-vs-default.phyphox");
        try {
            assertBuffer("toggle_init", 1);
            assertBuffer("dropdown_init", 2);
            assertBuffer("edit_init", 42);
            assertBuffer("slider_init", 4);

            assertBuffer("toggle_default", 1);
            assertBuffer("dropdown_default", 1);
            assertBuffer("edit_default", 7);
            assertBuffer("slider_default", 3);

            //Paging forces a read pass over every element, and a read pass must not turn into a write.
            page(activity, 1);
            page(activity, 0);

            assertBuffer("toggle_init", 1);
            assertBuffer("dropdown_init", 2);
            assertBuffer("edit_init", 42);
            assertBuffer("slider_init", 4);
            assertBuffer("toggle_default", 1);
            assertBuffer("dropdown_default", 1);
            assertBuffer("edit_default", 7);
            assertBuffer("slider_default", 3);
        } finally {
            FixtureExperiment.close(activity);
        }
    }

    //A control cannot show NaN: replaced by the unclamped default, and that is not user input
    @Test
    public void aNanIsReplacedByTheDefault() throws Exception {
        assumeTrue(FixtureExperiment.available("nan-vs-default.phyphox"));
        Experiment activity = FixtureExperiment.launch("nan-vs-default.phyphox");
        try {
            assertBuffer("toggle_nan", 1);
            assertBuffer("dropdown_nan", 2);
            assertBuffer("edit_nan", 12);
            assertBuffer("slider_nan", 3);
            assertTrue("replacing a NaN started the experiment", !activity.measuring);
        } finally {
            FixtureExperiment.close(activity);
        }
    }

    @Test
    public void editsWriteWhatTheyAccept() throws Exception {
        assumeTrue(FixtureExperiment.available("edits.phyphox"));
        Experiment activity = FixtureExperiment.launch("edits.phyphox");
        try {
            EditText plain = viewOf(activity, "plain", EditText.class);
            type(plain, "2.75");
            commit(plain);
            assertBuffer("plain", 2.75);

            //Out of range is clamped, not rejected.
            EditText bounded = viewOf(activity, "bounded", EditText.class);
            type(bounded, "99");
            commit(bounded);
            assertBuffer("bounded", 10);

            //The key listener refuses the minus; a negative that slips through is folded.
            EditText unsigned = viewOf(activity, "unsigned", EditText.class);
            type(unsigned, "-4");
            commit(unsigned);
            assertBuffer("unsigned", 4);

            //The decimal separator is refused, so "3.5" arrives as 35.
            EditText integer = viewOf(activity, "integer only", EditText.class);
            type(integer, "3.5");
            commit(integer);
            assertBuffer("integer", 35);

            //Shown in cm, stored in metres.
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

            //Two writes to one buffer: each clears its output first, so only the last remains.
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
            //The plain slider sits at 2.5 of 0..5; drag the thumb right.
            View plain = viewOf(activity, "plain",
                    com.google.android.material.slider.Slider.class);
            dragWithin(plain, 0.5f, 0.8f);
            double[] s1 = buffer("s1");
            if (s1.length > 0 && Math.abs(s1[0] - 2.5) < 1e-6) {
                //a dropped touch on a loaded emulator, not a finding
                dragWithin(plain, 0.5f, 0.8f);
                s1 = buffer("s1");
            }
            assertTrue("the plain slider did not move: " + Arrays.toString(s1),
                    s1.length > 0 && Math.abs(s1[0] - 2.5) > 1e-6);
            assertTrue("value outside the range: " + s1[0], s1[0] >= 0 && s1[0] <= 5);
            assertTrue("value off the 0.1 step grid: " + s1[0],
                    Math.abs(s1[0] * 10 - Math.round(s1[0] * 10)) < 1e-6);

            //The range slider holds 20 - 60 of 0..100; drag the upper thumb towards the end.
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
            //Material's exposed menu (AutoCompleteTextView, not a Spinner): the list is its own popup.
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
