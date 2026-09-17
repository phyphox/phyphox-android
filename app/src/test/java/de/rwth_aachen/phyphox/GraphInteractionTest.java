package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

// phyphox-test: graph-interaction
//The touch handling of GraphView without a GL surface: picking a point (tap, drag for a second
//one), panning and pinch zoom in data coordinates, and all of it over empty buffers, which is what
//crashed 1.2.1. GraphView is a plain canvas View; only the curve itself needs the GL surface, so
//the geometry can run on Robolectric. The maximized graph's chrome around it (markers, difference
//and fit read-outs, tools menu, pick outputs) is the instrumented half, GraphInteractionUiTest.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class GraphInteractionTest {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;

    private GraphView graph;
    //what the view reported: {index, dataX, dataY} per showPointInfo, the index per hidePointInfo
    private final List<float[]> shown = new ArrayList<>();
    private final List<Integer> hidden = new ArrayList<>();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        //a parent, because the gesture handlers ask it to stop intercepting
        FrameLayout parent = new FrameLayout(context);
        graph = new GraphView(context, new PlotAreaView(context), new PlotRenderer(context));
        graph.setCurves(1); //one x/y pair, as the graph element sets it up after the history length
        parent.addView(graph);
        graph.setPointInfoListener(new GraphView.PointInfo() {
            @Override
            public void showPointInfo(float viewX, float viewY, float pointX, float pointY, float pointZ, int index) {
                shown.add(new float[]{index, pointX, pointY});
            }

            @Override
            public void hidePointInfo(int index) {
                hidden.add(index);
            }
        });
        parent.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, WIDTH, HEIGHT);
    }

    // ------------------------------------------------------------------ data

    private static FloatBufferRepresentation values(float... v) {
        FloatBuffer data = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        data.put(v);
        return new FloatBufferRepresentation(data, 0, v.length);
    }

    //What DataBuffer.getFloatBuffer hands out for a buffer without values
    private static FloatBufferRepresentation nothing() {
        return new FloatBufferRepresentation(null, 0, 0);
    }

    @SuppressWarnings("unchecked")
    private void show(FloatBufferRepresentation x, double minX, double maxX, FloatBufferRepresentation y, double minY, double maxY) {
        graph.addGraphData(new FloatBufferRepresentation[]{y}, minY, maxY, new FloatBufferRepresentation[]{x}, minX, maxX,
                Double.NaN, Double.NaN, new List[1], new List[1]);
        uploaded();
    }

    //The point count of a curve is set by the render thread when it uploads the buffers (PlotRenderer.doUpdateBuffers),
    //and onDraw is where the plot area is measured - both have to have happened before a touch means anything
    private void uploaded() {
        for (CurveData curve : graph.graphSetup.dataSets)
            curve.n = curve.fbX == null ? (curve.fbY == null ? 0 : curve.fbY.size) : Math.min(curve.fbX.size, curve.fbY.size);
        graph.draw(new Canvas(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)));
    }

    //y = 2x + 1 on x = 0..8
    private void showLine() {
        show(values(0, 1, 2, 3, 4, 5, 6, 7, 8), 0, 8, values(1, 3, 5, 7, 9, 11, 13, 15, 17), 1, 17);
    }

    //An element whose containers hold nothing yet: null buffers and NaN bounds, as the graph element passes them
    private void showEmpty() {
        show(nothing(), Double.NaN, Double.NaN, nothing(), Double.NaN, Double.NaN);
    }

    // --------------------------------------------------------------- touches

    private float viewX(double dataX) {
        return (float) graph.dataXToViewX(dataX);
    }

    private float viewY(double dataY) {
        return (float) graph.dataYToViewY(dataY);
    }

    private float plotCenterX() {
        return graph.graphSetup.plotBoundL + graph.graphSetup.plotBoundW / 2f;
    }

    private float plotCenterY() {
        return graph.graphSetup.plotBoundT + graph.graphSetup.plotBoundH / 2f;
    }

    private void touch(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        graph.dispatchTouchEvent(event);
        event.recycle();
    }

    private void tap(float x, float y) {
        touch(MotionEvent.ACTION_DOWN, x, y);
        touch(MotionEvent.ACTION_UP, x, y);
    }

    private void drag(float fromX, float fromY, float toX, float toY) {
        touch(MotionEvent.ACTION_DOWN, fromX, fromY);
        for (int i = 1; i <= 5; i++)
            touch(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * i / 5f, fromY + (toY - fromY) * i / 5f);
        touch(MotionEvent.ACTION_UP, toX, toY);
    }

    private static MotionEvent.PointerProperties finger(int id) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = id;
        properties.toolType = MotionEvent.TOOL_TYPE_FINGER;
        return properties;
    }

    private static MotionEvent.PointerCoords at(float x, float y) {
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.pressure = 1;
        coords.size = 1;
        return coords;
    }

    private void twoFingers(int action, long downTime, float x0, float y0, float x1, float y1) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 2,
                new MotionEvent.PointerProperties[]{finger(0), finger(1)},
                new MotionEvent.PointerCoords[]{at(x0, y0), at(x1, y1)},
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        graph.dispatchTouchEvent(event);
        event.recycle();
    }

    //A horizontal pinch around (cx, cy): the fingers start "span" apart and end "factor" times as far apart
    private void pinch(float cx, float cy, float span, float factor) {
        long downTime = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, cx - span / 2, cy);
        twoFingers(MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), downTime,
                cx - span / 2, cy, cx + span / 2, cy);
        for (int i = 1; i <= 8; i++) {
            float half = span / 2 * (1 + (factor - 1) * i / 8f);
            SystemClock.sleep(20);
            twoFingers(MotionEvent.ACTION_MOVE, downTime, cx - half, cy, cx + half, cy);
        }
        float half = span / 2 * factor;
        twoFingers(MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), downTime,
                cx - half, cy, cx + half, cy);
        touch(MotionEvent.ACTION_UP, cx - half, cy);
    }

    // ------------------------------------------------------------- the tests

    @Test
    public void aTapPicksTheNearestPoint() {
        showLine();
        graph.setTouchMode(GraphView.TouchMode.pick);

        //a little off the point, the nearest one is still (4, 9)
        tap(viewX(4) + 6, viewY(9) - 6);

        assertThat(shown).hasSize(1);
        assertThat(shown.get(0)[0]).isEqualTo(0f);
        assertThat(shown.get(0)[1]).isWithin(1e-6f).of(4f);
        assertThat(shown.get(0)[2]).isWithin(1e-6f).of(9f);
    }

    @Test
    public void aDragPicksASecondPointForTheDifference() {
        showLine();
        graph.setTouchMode(GraphView.TouchMode.pick);

        drag(viewX(2), viewY(5), viewX(6), viewY(13));

        //the down event picks the first marker, the last move the second
        float[] first = shown.get(0);
        float[] second = shown.get(shown.size() - 1);
        assertThat(first[0]).isEqualTo(0f);
        assertThat(first[1]).isWithin(1e-6f).of(2f);
        assertThat(first[2]).isWithin(1e-6f).of(5f);
        assertThat(second[0]).isEqualTo(1f);
        assertThat(second[1]).isWithin(1e-6f).of(6f);
        assertThat(second[2]).isWithin(1e-6f).of(13f);
    }

    @Test
    public void aTapOutsideThePlotAreaPicksNothing() {
        showLine();
        graph.setTouchMode(GraphView.TouchMode.pick);

        //the axis label area left of the plot
        tap(graph.graphSetup.plotBoundL / 2f, plotCenterY());

        assertThat(shown).isEmpty();
    }

    @Test
    public void aTapFarFromAnyPointReportsNoPoint() {
        showLine();
        graph.setTouchMode(GraphView.TouchMode.pick);

        //the pick radius is 100 px; the line runs from the bottom left to the top right corner, so
        //the bottom right corner of the plot is at least the plot height/sqrt(2) away from it
        tap(graph.graphSetup.plotBoundL + graph.graphSetup.plotBoundW - 2,
                graph.graphSetup.plotBoundT + graph.graphSetup.plotBoundH - 2);

        assertThat(shown).hasSize(1);
        assertThat(shown.get(0)[1]).isNaN();
        assertThat(shown.get(0)[2]).isNaN();
    }

    @Test
    public void leavingPickModeClearsBothMarkers() {
        showLine();
        graph.setTouchMode(GraphView.TouchMode.pick);
        drag(viewX(2), viewY(5), viewX(6), viewY(13));
        hidden.clear();

        graph.setTouchMode(GraphView.TouchMode.off);

        assertThat(hidden).containsExactly(0, 1);
    }

    @Test
    public void pickingOnAnEmptyGraphDoesNotCrash() {
        showEmpty();
        graph.setTouchMode(GraphView.TouchMode.pick);

        tap(plotCenterX(), plotCenterY());
        drag(plotCenterX() - 50, plotCenterY(), plotCenterX() + 50, plotCenterY());

        for (float[] report : shown) {
            assertThat(report[1]).isNaN();
            assertThat(report[2]).isNaN();
        }
    }

    @Test
    public void pickingOnAnEmptyColorMapDoesNotCrash() {
        //A map is two curves: the x/y positions and a mapZ curve holding the values
        graph.setCurves(2);
        graph.setStyle(GraphView.Style.mapXY, 0);
        graph.setStyle(GraphView.Style.mapZ, 1);
        graph.setMapWidth(4, 0);
        graph.addGraphData(new FloatBufferRepresentation[]{nothing(), nothing()}, Double.NaN, Double.NaN,
                new FloatBufferRepresentation[]{nothing(), null}, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                new List[2], new List[2]);
        uploaded();
        graph.setTouchMode(GraphView.TouchMode.pick);

        tap(plotCenterX(), plotCenterY());

        for (float[] report : shown) {
            assertThat(report[1]).isNaN();
            assertThat(report[2]).isNaN();
        }
    }

    @Test
    public void aBufferShorterThanAnnouncedIsNotOverread() {
        //The point count comes from the last upload, the copy from the buffer: a buffer that shrank
        //in between (a clear during the gesture) must not throw a BufferUnderflowException
        show(values(0, 1, 2, 3), 0, 3, values(0, 1, 2, 3), 0, 3);
        graph.graphSetup.dataSets.get(0).n = 9;
        graph.setTouchMode(GraphView.TouchMode.pick);

        tap(viewX(2), viewY(2));

        assertThat(shown).hasSize(1);
        assertThat(shown.get(0)[1]).isWithin(1e-6f).of(2f);
    }

    @Test
    public void panningShiftsTheRangeAndKeepsItsWidth() {
        showLine();
        graph.setTouchMode(GraphView.TouchMode.zoom);
        double width = graph.maxX - graph.minX;
        float shift = 100;

        drag(plotCenterX() + shift / 2, plotCenterY(), plotCenterX() - shift / 2, plotCenterY());

        //dragging the content to the left brings larger x into view, by what the shift means in the
        //drawn range (which onDraw pads beyond the data)
        assertThat(graph.zoomState.minX).isWithin(1e-3).of(graph.viewXToDataX(graph.dataXToViewX(graph.minX) + shift));
        assertThat(graph.zoomState.maxX).isWithin(1e-3).of(graph.viewXToDataX(graph.dataXToViewX(graph.maxX) + shift));
        assertThat(graph.zoomState.maxX - graph.zoomState.minX).isWithin(1e-3).of(width);
        assertThat(graph.zoomState.minY).isWithin(1e-3).of(graph.minY);
        assertThat(graph.zoomState.maxY).isWithin(1e-3).of(graph.maxY);
        assertThat(graph.zoomState.follows).isFalse();
    }

    @Test
    public void aTapInZoomModeIsNotAPan() {
        showLine();
        graph.setTouchMode(GraphView.TouchMode.zoom);

        tap(plotCenterX(), plotCenterY());

        assertThat(graph.zoomState.minX).isNaN();
        assertThat(graph.zoomState.maxX).isNaN();
    }

    @Test
    public void aHorizontalPinchZoomsTheXAxisOnly() {
        showLine();
        graph.setTouchMode(GraphView.TouchMode.zoom);
        double width = graph.maxX - graph.minX;
        double height = graph.maxY - graph.minY;

        pinch(plotCenterX(), plotCenterY(), 200, 2);

        //Spreading the fingers shows less: the detector only counts the spread after its own slop,
        //so the factor is not exactly 2, but the range shrinks around the fingers and y stays
        double zoomedWidth = graph.zoomState.maxX - graph.zoomState.minX;
        double drawnWidth = graph.viewXToDataX(graph.graphSetup.plotBoundL + graph.graphSetup.plotBoundW) - graph.viewXToDataX(graph.graphSetup.plotBoundL);
        assertThat(zoomedWidth).isLessThan(0.7 * drawnWidth);
        assertThat(zoomedWidth).isGreaterThan(0.4 * drawnWidth);
        double focus = graph.viewXToDataX(plotCenterX());
        assertThat((graph.zoomState.minX + graph.zoomState.maxX) / 2).isWithin(0.05 * width).of(focus);
        assertThat(graph.zoomState.maxY - graph.zoomState.minY).isWithin(1e-3).of(height);
    }

    @Test
    public void pinchingAnEmptyGraphDoesNotCrash() {
        showEmpty();
        graph.setTouchMode(GraphView.TouchMode.zoom);

        pinch(plotCenterX(), plotCenterY(), 200, 2);
        drag(plotCenterX() + 50, plotCenterY(), plotCenterX() - 50, plotCenterY());
    }
}
