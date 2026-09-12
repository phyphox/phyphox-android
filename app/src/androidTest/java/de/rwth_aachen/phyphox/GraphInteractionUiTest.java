package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

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

// phyphox-test: graph-interaction
//The maximized graph's tools on fixtures/views/graphs-interaction.phyphox: expanding a graph,
//picking a point and committing it to its output buffers, dragging for the difference and slope
//read-out, the linear fit, panning and the zoom reset - and the same gestures on a graph over
//empty containers, which crashed 1.2.1. The touch geometry itself is GraphInteractionTest (T0).
//UiAutomator, not Espresso: an open experiment redraws continuously and never idles.
@RunWith(AndroidJUnit4.class)
public class GraphInteractionUiTest {
    private static final String FIXTURE = "graphs-interaction.phyphox";
    private static final int PORT = 8080;

    private Experiment activity;

    @Before
    public void launchFixture() throws Exception {
        assumeTrue("phyphox-docs was not checked out next to this repository at build time",
                FixtureExperiment.available(FIXTURE));
        //shell-only property, read when the experiment launches (see DebugSwitches)
        shell("setprop debug.phyphox.remote 1");
        shell("setprop debug.phyphox.remotePort " + PORT);
        activity = FixtureExperiment.launch(FIXTURE);
    }

    @After
    public void closeFixture() throws Exception {
        FixtureExperiment.close(activity);
        shell("setprop debug.phyphox.remote '\"\"'");
        shell("setprop debug.phyphox.remotePort '\"\"'");
    }

    private void shell(String command) throws Exception {
        device().executeShellCommand(command);
    }

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
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
    private void assertBuffer(String name, double expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        double[] values = new double[0];
        while (System.currentTimeMillis() < deadline) {
            try {
                values = buffer(name);
                if (values.length > 0 && Math.abs(values[values.length - 1] - expected) < 1e-6)
                    return;
            } catch (ConnectException e) {
                //not up yet
            }
            Thread.sleep(100);
        }
        assertTrue("buffer " + name + " is empty", values.length > 0);
        assertEquals("buffer " + name, expected, values[values.length - 1], 1e-6);
    }

    // -------------------------------------------------------------- the views

    private ExpView.expViewElement elementOf(String label) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            for (ExpView view : activity.experiment.experimentViews)
                for (ExpView.expViewElement element : view.elements)
                    if (label.equals(element.label) && element.rootView != null)
                        return element;
            if (System.currentTimeMillis() >= deadline)
                throw new AssertionError("No view for element \"" + label + "\" after 5 s");
            Thread.sleep(100);
        }
    }

    private InteractiveGraphView graphOf(String label) throws InterruptedException {
        View root = elementOf(label).rootView;
        assertTrue("element \"" + label + "\" is not a graph", root instanceof InteractiveGraphView);
        return (InteractiveGraphView) root;
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

    //Tapping a graph toggles its exclusive mode; the layout transition takes 150 ms
    private InteractiveGraphView maximize(String label) throws Exception {
        InteractiveGraphView graph = graphOf(label);
        tap(graph);
        Thread.sleep(1000);
        assertEquals("element \"" + label + "\" did not maximize", ExpView.State.maximized, elementOf(label).state);
        return graph;
    }

    private void selectTool(InteractiveGraphView graph, int toolId) throws Exception {
        View tool = graph.findViewById(toolId);
        assertNotNull("tool button missing", tool);
        tap(tool);
    }

    private int[] dataPoint(InteractiveGraphView graph, double dataX, double dataY) {
        final float[] view = new float[2];
        getInstrumentation().runOnMainSync(() -> {
            view[0] = (float) graph.graphView.dataXToViewX(dataX);
            view[1] = (float) graph.graphView.dataYToViewY(dataY);
        });
        return onScreen(graph.graphView, view[0], view[1]);
    }

    private int[] plotPoint(InteractiveGraphView graph, float fractionX, float fractionY) {
        final float[] view = new float[2];
        getInstrumentation().runOnMainSync(() -> {
            GraphSetup setup = graph.graphView.graphSetup;
            view[0] = setup.plotBoundL + setup.plotBoundW * fractionX;
            view[1] = setup.plotBoundT + setup.plotBoundH * fractionY;
        });
        return onScreen(graph.graphView, view[0], view[1]);
    }

    private void click(int[] point) throws InterruptedException {
        device().click(point[0], point[1]);
        Thread.sleep(600);
    }

    private void swipe(int[] from, int[] to) throws InterruptedException {
        device().swipe(from[0], from[1], to[0], to[1], 30);
        Thread.sleep(800);
    }

    //The read-out is a popup window whose text the view keeps; polls because the marker is set from the touch
    private String popupText(InteractiveGraphView graph) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        final String[] text = new String[1];
        while (true) {
            getInstrumentation().runOnMainSync(() ->
                    text[0] = graph.popupWindowText == null ? null : graph.popupWindowText.getText().toString());
            if (text[0] != null || System.currentTimeMillis() >= deadline)
                return text[0];
            Thread.sleep(100);
        }
    }

    //The pick outputs are buttons on the read-out; the theme may render their labels in capitals
    private UiObject2 readOutButton(String label) {
        return device().wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(label), Pattern.CASE_INSENSITIVE))), 3000);
    }

    private void assertReadOut(String text, String... fragments) {
        assertNotNull("no read-out popup appeared", text);
        for (String fragment : fragments)
            assertTrue("read-out lacks \"" + fragment + "\":\n" + text, text.contains(fragment));
    }

    private void chooseFromMoreTools(InteractiveGraphView graph, int titleResource) throws Exception {
        selectTool(graph, R.id.graph_tools_more);
        String title = activity.getString(titleResource);
        UiObject2 item = device().wait(Until.findObject(By.text(title)), 3000);
        assertNotNull("menu item \"" + title + "\" not shown", item);
        item.click();
        Thread.sleep(800);
    }

    private double zoomMinX(InteractiveGraphView graph) {
        final double[] value = new double[1];
        getInstrumentation().runOnMainSync(() -> value[0] = graph.graphView.zoomState.minX);
        return value[0];
    }

    // --------------------------------------------------------------- the tests

    @Test
    public void tappingAGraphMaximizesItAndRevealsTheTools() throws Exception {
        InteractiveGraphView graph = maximize("line");

        final int[] visibility = new int[2];
        getInstrumentation().runOnMainSync(() -> {
            visibility[0] = graph.findViewById(R.id.graph_toolbar).getVisibility();
            visibility[1] = graph.findViewById(R.id.graph_tools_pick).getVisibility();
        });
        assertEquals("toolbar", View.VISIBLE, visibility[0]);
        assertEquals("pick tool", View.VISIBLE, visibility[1]);
        assertEquals("the other graph stays out of the way", ExpView.State.hidden, elementOf("empty").state);

        //the collapse icon (a tap on the plot itself belongs to the pan tool) brings the page back
        tap(graph.findViewById(R.id.graph_collapse_image));
        Thread.sleep(1000);
        assertEquals(ExpView.State.normal, elementOf("line").state);
        assertEquals(ExpView.State.normal, elementOf("empty").state);
    }

    @Test
    public void pickingAPointReportsItAndWritesTheOutputs() throws Exception {
        InteractiveGraphView graph = maximize("line");
        selectTool(graph, R.id.graph_tools_pick);

        click(dataPoint(graph, 4, 9));

        assertReadOut(popupText(graph), "4.00000 s", "9.00000 m");

        //each pick output is a button on the read-out, writing the picked coordinate to its container
        UiObject2 pickX = readOutButton("Pick x");
        assertNotNull("pick button for x", pickX);
        pickX.click();
        assertBuffer("picked_x", 4);
        UiObject2 pickY = readOutButton("Pick y");
        assertNotNull("pick button for y", pickY);
        pickY.click();
        assertBuffer("picked_y", 9);
    }

    @Test
    public void draggingBetweenTwoPointsShowsDifferenceAndSlope() throws Exception {
        InteractiveGraphView graph = maximize("line");
        selectTool(graph, R.id.graph_tools_pick);

        swipe(dataPoint(graph, 2, 5), dataPoint(graph, 6, 13));

        assertReadOut(popupText(graph),
                activity.getString(R.string.graph_difference_label), "4.00000 s", "8.00000 m",
                activity.getString(R.string.graph_slope_label), "2.00000 m /", " s");
    }

    @Test
    public void theLinearFitReportsSlopeAndIntercept() throws Exception {
        InteractiveGraphView graph = maximize("line");

        chooseFromMoreTools(graph, R.string.graph_tools_linear_fit);

        assertReadOut(popupText(graph), activity.getString(R.string.graph_fit_label), "a = 2.00000", "b = 1.00000");
    }

    @Test
    public void panningMovesTheRangeAndResetRestoresIt() throws Exception {
        InteractiveGraphView graph = maximize("line");
        //pan and zoom is the tool a maximized graph starts with
        assertTrue(Double.isNaN(zoomMinX(graph)));

        swipe(plotPoint(graph, 0.7f, 0.5f), plotPoint(graph, 0.3f, 0.5f));

        //dragging the content to the left brings larger x into view: 40 % of the 0..8 range
        double minX = zoomMinX(graph);
        assertFalse("the range did not move", Double.isNaN(minX));
        assertEquals(3.2, minX, 0.4);

        chooseFromMoreTools(graph, R.string.graph_tools_reset);
        assertTrue("reset did not restore the automatic range", Double.isNaN(zoomMinX(graph)));
    }

    @Test
    public void everyToolSurvivesAGraphWithoutData() throws Exception {
        InteractiveGraphView graph = maximize("empty");

        swipe(plotPoint(graph, 0.7f, 0.5f), plotPoint(graph, 0.3f, 0.5f));
        selectTool(graph, R.id.graph_tools_pick);
        click(plotPoint(graph, 0.5f, 0.5f));
        swipe(plotPoint(graph, 0.3f, 0.5f), plotPoint(graph, 0.7f, 0.5f));
        chooseFromMoreTools(graph, R.string.graph_tools_linear_fit);

        //a crash would have taken the instrumentation down with the app; this pins the softer symptoms
        assertFalse("the experiment activity is gone", activity.isDestroyed() || activity.isFinishing());
        assertEquals(ExpView.State.maximized, elementOf("empty").state);
        final boolean[] attached = new boolean[1];
        getInstrumentation().runOnMainSync(() -> attached[0] = graph.isAttachedToWindow() && graph.getParent() instanceof ViewGroup);
        assertTrue("the graph view was torn down", attached[0]);
    }
}
