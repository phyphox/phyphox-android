package de.rwth_aachen.phyphox.ExperimentView.GraphView;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
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

import java.util.ArrayList;
import java.util.List;

// phyphox-test: unit-conversion-display
//The GraphView's share of the unit conversion (phyphox-docs docs/file-format/units.md): a converted axis shows
//nice tics in the display unit at the matching data positions, positions use scale and offset while differences
//use the scale alone (temperature), a clock axis and a text unit are not convertible, and a tap on an axis label
//text in an interactive mode reaches the listener while a tap inside the plot does not and a tap beside the label
//leaves the maximized graph.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class GraphUnitConversionTest {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 300;

    private GraphView graph;
    private final List<Integer> taps = new ArrayList<>();
    private int outsideTaps = 0;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        graph = new GraphView(context, new PlotAreaView(context), new PlotRenderer(context));
        graph.setCurves(1);
        graph.setLabel("x", "y", null, "s", "m/s²", null, null);
        graph.setUnitIds("second", "meter_per_square_second", null);
        graph.setScaleModeX(GraphView.ScaleMode.fixed, 0, GraphView.ScaleMode.fixed, 10);
        graph.setScaleModeY(GraphView.ScaleMode.fixed, 0, GraphView.ScaleMode.fixed, 10);
        parent.addView(graph);
        graph.setPointInfoListener(new GraphView.PointInfo() {
            @Override
            public void showPointInfo(float viewX, float viewY, float pointX, float pointY, float pointZ, int index) {
            }

            @Override
            public void hidePointInfo(int index) {
            }
        });
        graph.setAxisTapListener(taps::add);
        graph.setOutsideTapListener(() -> outsideTaps++);
        parent.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, WIDTH, HEIGHT);
    }

    private void draw() {
        graph.draw(new Canvas(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)));
    }

    @Test
    public void aConvertedAxisShowsNiceTicsInTheDisplayUnitAtTheDataPositions() {
        draw();
        assertThat(graph.lastYTicLabels).asList().containsExactly("0", "2", "4", "6", "8").inOrder();
        assertThat(graph.getLabelAndUnitY()).isEqualTo("y (m/s²)");

        graph.setDisplayUnit(GraphView.AXIS_Y, "foot_per_square_second");
        draw();
        assertThat(graph.getLabelAndUnitY()).isEqualTo("y (ft/s²)");
        assertThat(graph.lastYTicLabels).asList().containsExactly("0", "10", "20", "30").inOrder();
        //the tic labelled 10 ft/s² sits at 3.048 m/s² of the data
        GraphView.Tic ten = graph.graphSetup.yTics[1];
        assertThat(ten.display).isWithin(1e-9).of(10);
        assertThat(ten.value).isWithin(1e-9).of(3.048);
        //the plot range itself is untouched
        assertThat(graph.graphSetup.minY).isWithin(1e-6).of(0);
        assertThat(graph.graphSetup.maxY).isWithin(1e-6).of(10);

        graph.setDisplayUnit(GraphView.AXIS_Y, "meter_per_square_second");
        draw();
        assertThat(graph.lastYTicLabels).asList().containsExactly("0", "2", "4", "6", "8").inOrder();
        assertThat(graph.isConverted(GraphView.AXIS_Y)).isFalse();
    }

    @Test
    public void temperaturePositionsUseTheOffsetAndDifferencesOnlyTheScale() {
        graph.setLabel("x", "T", null, "s", "°C", null, null);
        graph.setUnitIds("second", "degree_celsius", null);
        graph.setDisplayUnit(GraphView.AXIS_Y, "degree_fahrenheit");
        assertThat(graph.toDisplay(GraphView.AXIS_Y, 20)).isWithin(1e-9).of(68);
        assertThat(graph.fromDisplay(GraphView.AXIS_Y, 68)).isWithin(1e-9).of(20);
        assertThat(graph.displayScale(GraphView.AXIS_Y)).isWithin(1e-9).of(1.8);
        graph.setDisplayUnit(GraphView.AXIS_Y, "kelvin");
        assertThat(graph.toDisplay(GraphView.AXIS_Y, 20)).isWithin(1e-9).of(293.15);
        assertThat(graph.displayScale(GraphView.AXIS_Y)).isWithin(1e-9).of(1);
        draw();
        assertThat(graph.lastYTicLabels).asList().containsExactly("274", "276", "278", "280", "282").inOrder();
    }

    @Test
    public void aClockAxisATextUnitAndAnotherQuantityAreRefused() {
        graph.setTimeRanges(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()); //as the element does before any time toggle
        graph.setTimeAxes(true, false);
        assertThat(graph.isAxisConvertible(GraphView.AXIS_X)).isTrue();
        graph.setAbsoluteTime(true);
        assertThat(graph.isAxisConvertible(GraphView.AXIS_X)).isFalse();
        graph.setAbsoluteTime(false);
        graph.setDisplayUnit(GraphView.AXIS_Y, "second");
        assertThat(graph.isConverted(GraphView.AXIS_Y)).isFalse();
        graph.setUnitIds(null, null, null);
        assertThat(graph.isAxisConvertible(GraphView.AXIS_Y)).isFalse();
        graph.setDisplayUnit(GraphView.AXIS_Y, "foot_per_square_second");
        assertThat(graph.getLabelAndUnitY()).isEqualTo("y (m/s²)");
    }

    private void tap(float x, float y) {
        long t = SystemClock.uptimeMillis();
        graph.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        graph.onTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0));
    }

    @Test
    public void aTapOnAnAxisLabelReachesTheListenerOnlyInAnInteractiveModeAndATapBesideItLeaves() {
        draw();
        GraphSetup s = graph.graphSetup;
        float font = graph.getResources().getDimensionPixelSize(de.rwth_aachen.phyphox.R.dimen.graph_font);
        float xLabelX = s.plotBoundL + s.plotBoundW / 2f, xLabelY = HEIGHT - 0.6f * font;
        float yLabelX = 0.6f * font, yLabelY = s.plotBoundT + s.plotBoundH / 2f;
        tap(xLabelX, xLabelY);
        assertThat(taps).isEmpty(); //not maximized: the whole element is one button
        assertThat(outsideTaps).isEqualTo(0);
        graph.setTouchMode(GraphView.TouchMode.zoom);
        tap(xLabelX, xLabelY);
        tap(yLabelX, yLabelY);
        tap(s.plotBoundL + s.plotBoundW / 2f, s.plotBoundT + s.plotBoundH / 2f);
        assertThat(taps).containsExactly(GraphView.AXIS_X, GraphView.AXIS_Y).inOrder();
        assertThat(outsideTaps).isEqualTo(0);
        //the band beside the label text and the tic label row are not the label: they leave the maximized graph
        tap(s.plotBoundL + 5, xLabelY);
        tap(xLabelX, s.plotBoundT + s.plotBoundH + 2);
        assertThat(taps).hasSize(2);
        assertThat(outsideTaps).isEqualTo(2);
        graph.setTouchMode(GraphView.TouchMode.pick);
        tap(xLabelX, xLabelY);
        assertThat(taps).containsExactly(GraphView.AXIS_X, GraphView.AXIS_Y, GraphView.AXIS_X).inOrder();
        //a text unit has no dialog, and its label does not leave either
        graph.setUnitIds(null, "meter_per_square_second", null);
        tap(xLabelX, xLabelY);
        assertThat(taps).hasSize(3);
        assertThat(outsideTaps).isEqualTo(2);
    }
}
