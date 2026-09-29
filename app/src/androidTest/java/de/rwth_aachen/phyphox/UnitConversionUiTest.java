package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
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
import java.util.regex.Pattern;

import de.rwth_aachen.phyphox.ExperimentView.EditElement;
import de.rwth_aachen.phyphox.ExperimentView.ExpView;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.GraphElement;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.GraphSetup;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.GraphView;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.InteractiveGraphView;
import de.rwth_aachen.phyphox.ExperimentView.ValueElement;

// phyphox-test: unit-conversion-ui
//The user-facing path of the unit conversion (phyphox-docs docs/file-format/units.md) on
//corpus/generated/unit-references.phyphox: tapping the unit of a value and of an edit element and an axis label of
//a maximized graph opens the unit dialog listing the quantity's units with the experiment's marked, choosing one
//changes the displayed text at once while /get still carries the original values, and the Unit system setting
//converts every convertible element on the next load and leaves the exclusions alone.
//UiAutomator, not Espresso: an open experiment redraws continuously and never idles.
@RunWith(AndroidJUnit4.class)
public class UnitConversionUiTest {
    private static final String FIXTURE = "unit-references.phyphox";
    private static final int PORT = 8080;

    private Experiment activity;

    @Before
    public void prepare() throws Exception {
        assumeTrue("phyphox-docs was not checked out next to this repository at build time",
                FixtureExperiment.available(FIXTURE));
        setting("experiment");
        shell("setprop debug.phyphox.remote 1");
        shell("setprop debug.phyphox.remotePort " + PORT);
    }

    @After
    public void closeFixture() throws Exception {
        FixtureExperiment.close(activity);
        setting(null);
        shell("setprop debug.phyphox.remote '\"\"'");
        shell("setprop debug.phyphox.remotePort '\"\"'");
    }

    private static void setting(String value) {
        Context app = getInstrumentation().getTargetContext();
        if (value == null)
            PreferenceManager.getDefaultSharedPreferences(app).edit().remove(Units.Setting.PREF_KEY).commit();
        else
            PreferenceManager.getDefaultSharedPreferences(app).edit().putString(Units.Setting.PREF_KEY, value).commit();
    }

    private void shell(String command) throws Exception {
        device().executeShellCommand(command);
    }

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

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
    private double lastValue(String name) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            try {
                double[] values = buffer(name);
                assertTrue("buffer " + name + " is empty", values.length > 0);
                return values[values.length - 1];
            } catch (ConnectException e) {
                if (System.currentTimeMillis() >= deadline)
                    throw e;
                Thread.sleep(100);
            }
        }
    }

    private ExpViewElement elementOf(String label) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            for (ExpView view : activity.experiment.experimentViews)
                for (ExpViewElement element : view.elements)
                    if (label.equals(element.label) && element.rootView != null)
                        return element;
            if (System.currentTimeMillis() >= deadline)
                throw new AssertionError("No view for element \"" + label + "\" after 5 s");
            Thread.sleep(100);
        }
    }

    private int[] onScreen(View view, float x, float y) {
        final int[] point = new int[2];
        getInstrumentation().runOnMainSync(() -> {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            point[0] = location[0] + Math.round(x);
            point[1] = location[1] + Math.round(y);
        });
        return point;
    }

    private void tap(View view) throws Exception {
        getInstrumentation().runOnMainSync(() ->
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true));
        Thread.sleep(400);
        int[] point = onScreen(view, view.getWidth() / 2f, view.getHeight() / 2f);
        device().click(point[0], point[1]);
        Thread.sleep(600);
    }

    //The value's text view is the second child of its row; the edit's unit view the second child of the right half
    private TextView valueView(ValueElement value) {
        return (TextView) ((LinearLayout) value.rootView).getChildAt(1);
    }

    private TextView unitView(EditElement edit) {
        return (TextView) ((LinearLayout) ((LinearLayout) edit.rootView).getChildAt(1)).getChildAt(1);
    }

    private String text(TextView view) {
        final String[] text = new String[1];
        getInstrumentation().runOnMainSync(() -> text[0] = view.getText().toString());
        return text[0];
    }

    //Waits for the main loop to render the value (it runs every 40 ms)
    private String awaitValueText(ValueElement value, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        String text = text(valueView(value));
        while (!expected.equals(text) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            text = text(valueView(value));
        }
        return text;
    }

    private UiObject2 dialogEntry(String label) {
        return device().wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(label)))), 3000);
    }

    private void chooseUnit(String symbol) throws InterruptedException {
        UiObject2 entry = dialogEntry(symbol);
        assertNotNull("unit \"" + symbol + "\" not listed in the dialog", entry);
        entry.click();
        Thread.sleep(600);
    }

    @Test
    public void tappingTheUnitOfAValueOpensTheDialogAndSwitchesTheUnit() throws Exception {
        activity = FixtureExperiment.launch(FIXTURE);
        ValueElement distance = (ValueElement) elementOf("Distance");
        assertEquals("150.0 cm", awaitValueText(distance, "150.0 cm"));
        tap(valueView(distance));
        UiObject2 title = device().wait(Until.findObject(By.text(activity.getString(R.string.unit_dialog_title))), 3000);
        assertNotNull("the unit dialog did not open", title);
        assertNotNull("the experiment's unit is marked as its default",
                dialogEntry("cm (" + activity.getString(R.string.unit_dialog_experiment_default) + ")"));
        assertNotNull("the imperial group is listed", dialogEntry("in"));
        chooseUnit("m");
        assertEquals("1.500 m", awaitValueText(distance, "1.500 m"));
        assertEquals("the data is untouched", 1.5, lastValue("distance"), 1e-9);
        //a text unit has no dialog
        ValueElement jerk = (ValueElement) elementOf("Jerk");
        tap(valueView(jerk));
        assertEquals(null, device().wait(Until.findObject(By.text(activity.getString(R.string.unit_dialog_title))), 1000));
    }

    @Test
    public void tappingTheUnitOfAnEditElementConvertsTheFieldAndTheTypedValueGoesBackConverted() throws Exception {
        activity = FixtureExperiment.launch(FIXTURE);
        EditElement length = (EditElement) elementOf("Length");
        assertEquals("m", text(unitView(length)));
        tap(unitView(length));
        chooseUnit("ft");
        assertEquals("ft", text(unitView(length)));
        assertEquals("1.64042", length.displayedText().toString());
        assertEquals(0.5, lastValue("length"), 1e-9);
    }

    @Test
    public void tappingAnAxisLabelOfAMaximizedGraphOpensTheDialogForThatAxis() throws Exception {
        activity = FixtureExperiment.launch(FIXTURE);
        GraphElement acceleration = (GraphElement) elementOf("Acceleration");
        InteractiveGraphView graph = (InteractiveGraphView) acceleration.rootView;
        tap(graph);
        Thread.sleep(1000);
        assertEquals("the graph did not maximize", ExpView.State.maximized, acceleration.state);
        //the y label area is left of the plot
        final float[] point = new float[2];
        getInstrumentation().runOnMainSync(() -> {
            GraphSetup setup = graph.graphView.graphSetup;
            point[0] = setup.plotBoundL / 2f;
            point[1] = setup.plotBoundT + setup.plotBoundH / 2f;
        });
        int[] screen = onScreen(graph.graphView, point[0], point[1]);
        device().click(screen[0], screen[1]);
        Thread.sleep(600);
        assertNotNull("the unit dialog did not open", device().wait(Until.findObject(By.text(activity.getString(R.string.unit_dialog_title))), 3000));
        assertNotNull(dialogEntry("m/s² (" + activity.getString(R.string.unit_dialog_experiment_default) + ")"));
        chooseUnit("ft/s²");
        final String[] title = new String[1];
        getInstrumentation().runOnMainSync(() -> title[0] = graph.graphView.getLabelAndUnitY());
        assertEquals("a (ft/s²)", title[0]);
        assertEquals("foot_per_square_second", acceleration.getDisplayUnitId(GraphView.AXIS_Y));
        assertTrue("still maximized", acceleration.state == ExpView.State.maximized);
    }

    @Test
    public void theUnitSystemSettingConvertsOnTheNextLoadAndSkipsTheExclusions() throws Exception {
        setting("imperial");
        activity = FixtureExperiment.launch(FIXTURE);
        ValueElement distance = (ValueElement) elementOf("Distance");
        assertEquals("59.06 in", awaitValueText(distance, "59.06 in"));
        ValueElement temperature = (ValueElement) elementOf("Temperature");
        assertEquals("70.7 °F", awaitValueText(temperature, "70.7 °F"));
        ValueElement jerk = (ValueElement) elementOf("Jerk");
        assertFalse(jerk.isConvertible());
        assertEquals("m/s³", jerk.getUnit().text);
        EditElement length = (EditElement) elementOf("Length");
        assertEquals("ft", text(unitView(length)));
        GraphElement acceleration = (GraphElement) elementOf("Acceleration");
        final String[] titles = new String[2];
        getInstrumentation().runOnMainSync(() -> {
            titles[0] = ((InteractiveGraphView) acceleration.rootView).graphView.getLabelAndUnitX();
            titles[1] = ((InteractiveGraphView) acceleration.rootView).graphView.getLabelAndUnitY();
        });
        assertEquals("t (s)", titles[0]);
        assertEquals("a (ft/s²)", titles[1]);
        assertEquals(1.5, lastValue("distance"), 1e-9);
        assertEquals(21.5, lastValue("temperature"), 1e-9);
    }
}
