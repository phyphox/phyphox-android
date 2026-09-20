package de.rwth_aachen.phyphox.ExperimentView.GraphView;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
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
import java.util.List;
import de.rwth_aachen.phyphox.FloatBufferRepresentation;

// phyphox-test: graph-empty-state
//An empty plot area says why it is empty: "No data" while nothing has been measured, "No valid data"
//when everything is NaN or one axis has no values, and "No data in range" with an arrow towards the
//nearest point when valid data lies outside the current zoom. The classification is done in
//GraphView.onDraw, on the plain canvas View, so it runs on Robolectric without the GL surface.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class GraphEmptyStateTest {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;
    private static final float TAGGED_INVALID = -3.4e38f; //what DataBuffer.getFloatBuffer writes for NaN and infinities

    private GraphView graph;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        graph = new GraphView(context, new PlotAreaView(context), new PlotRenderer(context));
        graph.setCurves(1);
        parent.addView(graph);
        graph.setPointInfoListener(new GraphView.PointInfo() { //onDraw reports the picked points to it unconditionally
            @Override
            public void showPointInfo(float viewX, float viewY, float pointX, float pointY, float pointZ, int index) {
            }

            @Override
            public void hidePointInfo(int index) {
            }
        });
        parent.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, WIDTH, HEIGHT);
    }

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
    }

    private void draw() {
        drawOnto(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888));
    }

    //Draw over a white canvas; the plot area itself is left to the GL surface, so anything inside it is the status note
    private Bitmap drawOnto(Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        graph.draw(canvas);
        return bitmap;
    }

    private static boolean anyInk(Bitmap bitmap, RectF area) {
        for (int y = Math.max(0, (int) Math.floor(area.top)); y <= Math.min(bitmap.getHeight() - 1, (int) Math.ceil(area.bottom)); y++)
            for (int x = Math.max(0, (int) Math.floor(area.left)); x <= Math.min(bitmap.getWidth() - 1, (int) Math.ceil(area.right)); x++)
                if (bitmap.getPixel(x, y) != Color.WHITE)
                    return true;
        return false;
    }

    //y = 2x + 1 on x = 0..8
    private void showLine() {
        show(values(0, 1, 2, 3, 4, 5, 6, 7, 8), 0, 8, values(1, 3, 5, 7, 9, 11, 13, 15, 17), 1, 17);
    }

    private double plotLeft() {
        return graph.graphSetup.plotBoundL;
    }

    private double plotRight() {
        return graph.graphSetup.plotBoundL + graph.graphSetup.plotBoundW;
    }

    private double plotTop() {
        return graph.graphSetup.plotBoundT;
    }

    private double plotBottom() {
        return graph.graphSetup.plotBoundT + graph.graphSetup.plotBoundH;
    }

    @Test
    public void nothingAddedYetIsNoData() {
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noData);
    }

    @Test
    public void emptyBuffersAreNoData() {
        show(nothing(), Double.NaN, Double.NaN, nothing(), Double.NaN, Double.NaN);
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noData);
    }

    @Test
    public void allInvalidIsNoValidData() {
        show(values(0, 1, 2), 0, 2, values(TAGGED_INVALID, Float.NaN, TAGGED_INVALID), Double.NaN, Double.NaN);
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noValidData);
    }

    @Test
    public void oneEmptyAxisIsNoValidData() {
        show(values(0, 1, 2), 0, 2, nothing(), Double.NaN, Double.NaN);
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noValidData);
    }

    @Test
    public void invalidOnOneAxisOnlyWhereTheOtherIsValidIsNoValidData() {
        //x is valid where y is not and vice versa: not a single drawable pair
        show(values(0, TAGGED_INVALID), 0, 0, values(TAGGED_INVALID, 5), 5, 5);
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noValidData);
    }

    @Test
    public void visibleDataIsOk() {
        showLine();
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.ok);
    }

    @Test
    public void aSingleValidPointAmongInvalidOnesIsOk() {
        show(values(0, 1, 2), 0, 2, values(TAGGED_INVALID, 3, TAGGED_INVALID), 3, 3);
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.ok);
    }

    @Test
    public void zoomedPastTheDataPointsBackToIt() {
        showLine();
        //the line ends at x = 8; look at x = 20..30 and the y range it covers
        graph.zoomState.minX = 20;
        graph.zoomState.maxX = 30;
        graph.zoomState.minY = 0;
        graph.zoomState.maxY = 20;
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noDataInRange);
        //the nearest point (8, 17) is left of the plot area and within its vertical extent
        assertThat(graph.nearestVX).isLessThan(plotLeft());
        assertThat(graph.nearestVY).isAtLeast(plotTop());
        assertThat(graph.nearestVY).isAtMost(plotBottom());
        assertThat(graph.nearestVX).isWithin(0.5).of(graph.dataXToViewX(8));
        //so the arrow points left
        assertThat(Math.abs(graph.arrowAngle)).isWithin(15).of(180);
    }

    @Test
    public void zoomedBelowTheDataPointsUp() {
        showLine();
        graph.zoomState.minX = 0;
        graph.zoomState.maxX = 8;
        graph.zoomState.minY = -30;
        graph.zoomState.maxY = -10;
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noDataInRange);
        //(0, 1) is the closest to the plot area: straight above it
        assertThat(graph.nearestVY).isLessThan(plotTop());
        assertThat(graph.nearestVX).isAtLeast(plotLeft());
        assertThat(graph.nearestVX).isAtMost(plotRight());
        assertThat(graph.nearestVY).isWithin(0.5).of(graph.dataYToViewY(1));
        //so the arrow points upwards, seen from the plot centre (screen y grows downwards, so up is negative)
        assertThat(graph.arrowAngle).isLessThan(0.0);
        assertThat(graph.arrowAngle).isGreaterThan(-180.0);
    }

    @Test
    public void theArrowIsDrawnNextToTheNoteAndOnlyThere() {
        showLine();
        graph.zoomState.minX = 20;
        graph.zoomState.maxX = 30;
        graph.zoomState.minY = 0;
        graph.zoomState.maxY = 20;
        Bitmap withArrow = drawOnto(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888));
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noDataInRange);
        RectF arrow = graph.arrowRect;
        assertThat(arrow).isNotNull();
        //inside the plot area, to the right of its centre where the note ends
        assertThat(arrow.left).isGreaterThan((float) graph.graphSetup.plotBoundL + graph.graphSetup.plotBoundW / 2f);
        assertThat(arrow.right).isLessThan((float) graph.graphSetup.plotBoundL + graph.graphSetup.plotBoundW);
        assertThat(anyInk(withArrow, arrow)).isTrue();

        //the plain "No data" note is narrower, so the same spot stays blank without an arrow
        graph.setCurves(1);
        graph.zoomState.minX = Double.NaN;
        graph.zoomState.maxX = Double.NaN;
        graph.zoomState.minY = Double.NaN;
        graph.zoomState.maxY = Double.NaN;
        Bitmap without = drawOnto(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888));
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noData);
        assertThat(graph.arrowRect).isNull();
        assertThat(Double.isNaN(graph.arrowAngle)).isTrue();
        assertThat(anyInk(without, arrow)).isFalse();
    }

    @Test
    public void zoomingBackOntoTheDataIsOkAgain() {
        showLine();
        graph.zoomState.minX = 20;
        graph.zoomState.maxX = 30;
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noDataInRange);

        graph.zoomState.minX = Double.NaN;
        graph.zoomState.maxX = Double.NaN;
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.ok);
    }

    @Test
    public void fixedRangeAwayFromTheDataIsNoDataInRange() {
        graph.setScaleModeX(GraphView.ScaleMode.fixed, 100, GraphView.ScaleMode.fixed, 200);
        graph.setScaleModeY(GraphView.ScaleMode.fixed, 0, GraphView.ScaleMode.fixed, 20);
        showLine();
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noDataInRange);
        assertThat(graph.nearestVX).isLessThan(plotLeft());
    }

    // ------------------------------------------------ a single distinct value

    private static double[] ticValues(GraphView.Tic[] tics) {
        double[] values = new double[tics.length];
        for (int i = 0; i < tics.length; i++)
            values[i] = tics[i].value;
        return values;
    }

    @Test
    public void aSinglePointAtZeroGetsARangeAndOneTicPerAxis() {
        show(values(0), 0, 0, values(0), 0, 0);
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.ok);
        assertThat(graph.graphSetup.maxX).isGreaterThan(graph.graphSetup.minX);
        assertThat(graph.graphSetup.maxY).isGreaterThan(graph.graphSetup.minY);
        assertThat(ticValues(graph.graphSetup.xTics)).isEqualTo(new double[]{0});
        assertThat(ticValues(graph.graphSetup.yTics)).isEqualTo(new double[]{0});
        assertThat(graph.graphSetup.xTics[0].precision).isEqualTo(0);
    }

    @Test
    public void aSingleNegativeValueDoesNotInvertTheAxis() {
        show(values(-10, -10, -10), -10, -10, values(1, 2, 3), 1, 3);
        draw();
        assertThat(graph.graphSetup.maxX).isGreaterThan(graph.graphSetup.minX);
        assertThat(graph.graphSetup.minX).isLessThan(-10.0);
        assertThat(graph.graphSetup.maxX).isGreaterThan(-10.0);
        assertThat(ticValues(graph.graphSetup.xTics)).isEqualTo(new double[]{-10});
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.ok);
    }

    @Test
    public void theSingleTicShowsTheValueExactly() {
        show(values(0, 1, 2), 0, 2, values(9.81f, 9.81f, 9.81f), 9.81, 9.81); //the bounds arrive as the buffer's doubles, only the points are floats
        draw();
        assertThat(graph.graphSetup.yTics).hasLength(1);
        assertThat(graph.graphSetup.yTics[0].value).isWithin(1e-6).of(9.81);
        assertThat(graph.graphSetup.yTics[0].precision).isEqualTo(2);
    }

    @Test
    public void aSingleValueOnALogAxisOpensAroundIt() {
        graph.setLogScale(false, true, false);
        show(values(0, 1, 2), 0, 2, values(100, 100, 100), 100, 100);
        draw();
        assertThat(graph.graphSetup.minY).isLessThan(100.0);
        assertThat(graph.graphSetup.maxY).isGreaterThan(100.0);
        assertThat(graph.graphSetup.minY).isGreaterThan(0.0);
        assertThat(ticValues(graph.graphSetup.yTics)).isEqualTo(new double[]{100});
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.ok);
    }

    @Test
    public void aSingleValueOnATimeAxisOpensAroundIt() {
        graph.setTimeAxes(true, false);
        show(values(0), 0, 0, values(5), 5, 5);
        draw();
        assertThat(graph.graphSetup.maxX).isGreaterThan(graph.graphSetup.minX);
        assertThat(ticValues(graph.graphSetup.xTics)).isEqualTo(new double[]{0});
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.ok);
    }

    @Test
    public void theOpenedRangeDoesNotStickToAnExtendAxis() {
        //an extend axis starting at 3..3 with all values at 3 shows the opened range...
        graph.setScaleModeX(GraphView.ScaleMode.extend, 3, GraphView.ScaleMode.extend, 3);
        show(values(3, 3, 3), 3, 3, values(1, 2, 3), 1, 3);
        draw();
        assertThat(graph.graphSetup.maxX).isGreaterThan(graph.graphSetup.minX);
        assertThat(graph.graphSetup.xTics).hasLength(1);
        //...but its own bounds stay at the data
        assertThat(graph.minX).isEqualTo(3.0);
        assertThat(graph.maxX).isEqualTo(3.0);

        //new data extends from 3, not from the opened range
        show(values(3, 3.5f, 4), 3, 4, values(1, 2, 3), 1, 3);
        draw();
        assertThat(graph.minX).isEqualTo(3.0);
        assertThat(graph.maxX).isEqualTo(4.0);
        assertThat(graph.graphSetup.minX).isWithin(1e-6).of(3 - 0.05);
        assertThat(graph.graphSetup.maxX).isWithin(1e-6).of(4 + 0.05);
        assertThat(graph.graphSetup.xTics.length).isGreaterThan(1);
    }

    // ------------------------------------------------------------ headroom

    @Test
    public void onlyDataDeterminedEndsGetHeadroom() {
        //fixed min 0, auto max: the data up to 17 gets one 5 % pad, the fixed end none
        graph.setScaleModeY(GraphView.ScaleMode.fixed, 0, GraphView.ScaleMode.auto, 0);
        showLine();
        draw();
        assertThat(graph.graphSetup.minY).isWithin(1e-6).of(0);
        assertThat(graph.graphSetup.maxY).isWithin(1e-6).of(17 + 17 * 0.05);
        //x is auto at both ends
        assertThat(graph.graphSetup.minX).isWithin(1e-6).of(-8 * 0.05);
        assertThat(graph.graphSetup.maxX).isWithin(1e-6).of(8 + 8 * 0.05);
    }

    @Test
    public void aFullyFixedRangeIsShownExactly() {
        graph.setScaleModeX(GraphView.ScaleMode.fixed, -2, GraphView.ScaleMode.fixed, 10);
        graph.setScaleModeY(GraphView.ScaleMode.fixed, -1, GraphView.ScaleMode.fixed, 6);
        showLine();
        draw();
        assertThat(graph.graphSetup.minX).isWithin(1e-6).of(-2);
        assertThat(graph.graphSetup.maxX).isWithin(1e-6).of(10);
        assertThat(graph.graphSetup.minY).isWithin(1e-6).of(-1);
        assertThat(graph.graphSetup.maxY).isWithin(1e-6).of(6);
    }

    @Test
    public void aZoomedRangeIsShownExactly() {
        showLine();
        graph.zoomState.minX = 2;
        graph.zoomState.maxX = 6;
        graph.zoomState.minY = 3;
        graph.zoomState.maxY = 9;
        draw();
        assertThat(graph.graphSetup.minX).isWithin(1e-6).of(2);
        assertThat(graph.graphSetup.maxX).isWithin(1e-6).of(6);
        assertThat(graph.graphSetup.minY).isWithin(1e-6).of(3);
        assertThat(graph.graphSetup.maxY).isWithin(1e-6).of(9);
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.ok);
    }

    @Test
    public void nonPositiveValuesOnALogAxisCountAsOutOfRangeNotInvalid() {
        graph.setLogScale(true, false, false);
        show(values(-3, -2, -1), -3, -1, values(1, 2, 3), 1, 3);
        draw();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.noDataInRange);
        assertThat(graph.nearestVX).isLessThan(plotLeft());
    }
}
