package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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

// phyphox-test: graph-follow-x
//A graph with followX shows the newest data from the first frame on: the minX..maxX attributes only set the
//width of the window, which slides to the newest x as soon as data arrives, not first after a zoom gesture
//(the iOS bug of 2026-09-20). Configured the way the graph element does it: fixed scale modes with the
//attribute values, then followX. The displayed range is read from graphSetup after a draw, like
//GraphEmptyStateTest does; the T1 follow-x golden shows the same but cannot tell a stale window from a
//followed one by itself.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class GraphFollowXTest {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 400;

    private GraphView graph;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        graph = new GraphView(context, new PlotAreaView(context), new PlotRenderer(context));
        graph.setCurves(1);
        parent.addView(graph);
        graph.setPointInfoListener(new GraphView.PointInfo() {
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

        //followX="true" scaleMinX="fixed" scaleMaxX="fixed" minX="-5" maxX="0", as the follow-x fixture has it
        graph.setScaleModeX(GraphView.scaleMode.fixed, -5, GraphView.scaleMode.fixed, 0);
        graph.setScaleModeY(GraphView.scaleMode.auto, 0, GraphView.scaleMode.auto, 0);
        graph.setFollowX(true);
    }

    private static FloatBufferRepresentation values(float... v) {
        FloatBuffer data = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        data.put(v);
        return new FloatBufferRepresentation(data, 0, v.length);
    }

    @SuppressWarnings("unchecked")
    private void show(FloatBufferRepresentation x, double minX, double maxX, FloatBufferRepresentation y, double minY, double maxY) {
        graph.addGraphData(new FloatBufferRepresentation[]{y}, minY, maxY, new FloatBufferRepresentation[]{x}, minX, maxX,
                Double.NaN, Double.NaN, new List[1], new List[1]);
    }

    private void draw() {
        graph.draw(new Canvas(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)));
    }

    @Test
    public void withoutDataTheAttributeWindowIsShown() {
        draw();
        assertThat(graph.zoomState.follows).isTrue();
        assertThat(graph.graphSetup.minX).isWithin(1e-6).of(-5);
        assertThat(graph.graphSetup.maxX).isWithin(1e-6).of(0);
    }

    @Test
    public void theFirstUpdateAlreadyFollowsTheNewestData() {
        //y = 2x + 1 on x = 0..8, i.e. entirely beyond maxX = 0
        show(values(0, 1, 2, 3, 4, 5, 6, 7, 8), 0, 8, values(1, 3, 5, 7, 9, 11, 13, 15, 17), 1, 17);
        draw();

        //the 5-wide window ends at the newest x, shown exactly (a followed range gets no headroom)
        assertThat(graph.graphSetup.minX).isWithin(1e-6).of(3);
        assertThat(graph.graphSetup.maxX).isWithin(1e-6).of(8);
        assertThat(graph.zoomState.follows).isTrue();
        assertThat(graph.dataStatus).isEqualTo(GraphView.DataStatus.ok);
    }

    @Test
    public void laterUpdatesKeepFollowing() {
        show(values(0, 1, 2, 3, 4, 5, 6, 7, 8), 0, 8, values(1, 3, 5, 7, 9, 11, 13, 15, 17), 1, 17);
        draw();
        show(values(0, 2, 4, 6, 8, 10, 12), 0, 12, values(1, 5, 9, 13, 17, 21, 25), 1, 25);
        draw();

        assertThat(graph.graphSetup.minX).isWithin(1e-6).of(7);
        assertThat(graph.graphSetup.maxX).isWithin(1e-6).of(12);
    }

    @Test
    public void theWindowWidthComesFromTheAttributes() {
        graph.setScaleModeX(GraphView.scaleMode.fixed, 0, GraphView.scaleMode.fixed, 2);
        graph.setFollowX(true);
        show(values(0, 1, 2, 3, 4, 5, 6, 7, 8), 0, 8, values(1, 3, 5, 7, 9, 11, 13, 15, 17), 1, 17);
        draw();

        assertThat(graph.graphSetup.minX).isWithin(1e-6).of(6);
        assertThat(graph.graphSetup.maxX).isWithin(1e-6).of(8);
    }
}
