package de.rwth_aachen.phyphox.ExperimentView.GraphView;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
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

//The question when leaving a maximized graph with a zoom: no question without a zoom (also with the time axis on system
//time), the per-axis controls start from the emphasised button, a choice lands in the zoom state with the followX
//fallback, and the range lines show the zoom as the tic labels would.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class ApplyZoomChoiceTest {
    private GraphView graph;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        graph = new GraphView(context, new PlotAreaView(context), new PlotRenderer(context));
        graph.setCurves(1);
        graph.setLabel("t", "a", null, "s", "m/s²", null, null);
        graph.setUnitIds("second", "meter_per_square_second", null);
        graph.setScaleModeX(GraphView.ScaleMode.fixed, 0, GraphView.ScaleMode.fixed, 10);
        graph.setScaleModeY(GraphView.ScaleMode.fixed, 0, GraphView.ScaleMode.fixed, 10);
        parent.addView(graph);
        parent.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 600, 300);
    }

    @Test
    public void noQuestionWhenNothingIsZoomedEvenOnSystemTime() {
        assertThat(ApplyZoomChoice.anyZoomed(graph.zoomState)).isFalse();
        graph.setTimeRanges(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        graph.setTimeAxes(true, false);
        graph.setAbsoluteTime(true);
        assertThat(ApplyZoomChoice.anyZoomed(graph.zoomState)).isFalse();
        graph.zoomState.minY = 1;
        graph.zoomState.maxY = 3;
        assertThat(ApplyZoomChoice.anyZoomed(graph.zoomState)).isTrue();
        assertThat(ApplyZoomChoice.isZoomed(graph.zoomState, GraphView.AXIS_X)).isFalse();
        assertThat(ApplyZoomChoice.isZoomed(graph.zoomState, GraphView.AXIS_Y)).isTrue();
    }

    @Test
    public void perAxisControlsStartFromTheEmphasisedButton() {
        GraphView.ZoomState z = graph.zoomState;
        z.minX = 2; z.maxX = 4;
        z.minY = 1; z.maxY = 3;
        assertThat(ApplyZoomChoice.defaultAction(false)).isEqualTo(ApplyZoomChoice.Action.RESET);
        assertThat(ApplyZoomChoice.defaultAction(true)).isEqualTo(ApplyZoomChoice.Action.KEEP);
        for (int axis = 0; axis < 3; axis++)
            assertThat(ApplyZoomChoice.initialAxisAction(z, axis, ApplyZoomChoice.Action.RESET, true)).isEqualTo(ApplyZoomChoice.Action.RESET);
        assertThat(ApplyZoomChoice.initialAxisAction(z, GraphView.AXIS_X, ApplyZoomChoice.Action.KEEP, true)).isEqualTo(ApplyZoomChoice.Action.KEEP);
        assertThat(ApplyZoomChoice.initialAxisAction(z, GraphView.AXIS_Y, ApplyZoomChoice.Action.KEEP, true)).isEqualTo(ApplyZoomChoice.Action.KEEP);
        assertThat(ApplyZoomChoice.initialAxisAction(z, GraphView.AXIS_Z, ApplyZoomChoice.Action.KEEP, true)).isEqualTo(ApplyZoomChoice.Action.RESET); //not zoomed
        z.follows = true;
        assertThat(ApplyZoomChoice.initialAxisAction(z, GraphView.AXIS_X, ApplyZoomChoice.Action.KEEP, true)).isEqualTo(ApplyZoomChoice.Action.FOLLOW);
        assertThat(ApplyZoomChoice.initialAxisAction(z, GraphView.AXIS_X, ApplyZoomChoice.Action.KEEP, false)).isEqualTo(ApplyZoomChoice.Action.KEEP);
    }

    @Test
    public void applyingAChoiceWritesTheZoomStateWithTheFollowFallback() {
        GraphView.ZoomState z = graph.zoomState;
        z.minX = 2; z.maxX = 4;
        z.minY = 1; z.maxY = 3;
        ApplyZoomChoice.apply(graph, ApplyZoomChoice.Action.RESET, ApplyZoomChoice.Action.KEEP, ApplyZoomChoice.Action.RESET);
        assertThat(z.minX).isNaN();
        assertThat(z.maxX).isNaN();
        assertThat(z.minY).isEqualTo(1.0);
        assertThat(z.maxY).isEqualTo(3.0);
        assertThat(z.follows).isFalse();
        assertThat(graph.zoomState.previouslyKept).isTrue();

        z.minX = 2; z.maxX = 4;
        ApplyZoomChoice.apply(graph, ApplyZoomChoice.Action.FOLLOW, ApplyZoomChoice.Action.RESET, ApplyZoomChoice.Action.RESET);
        assertThat(z.maxX - z.minX).isWithin(1e-9).of(2.0); //rescale moves a following window to the newest data
        assertThat(z.follows).isTrue();
        assertThat(z.minY).isNaN();

        graph.followX = true;
        ApplyZoomChoice.apply(graph, ApplyZoomChoice.Action.RESET, ApplyZoomChoice.Action.RESET, ApplyZoomChoice.Action.RESET);
        assertThat(z.follows).isTrue();
        assertThat(z.maxX - z.minX).isWithin(1e-9).of(graph.maxX - graph.minX); //the configured window, following
        assertThat(graph.zoomState.previouslyKept).isFalse();
    }

    @Test
    public void rangeLinesShowTheZoomInTheDisplayUnitLikeTheTicLabels() {
        GraphView.ZoomState z = graph.zoomState;
        assertThat(graph.zoomRangeLine(GraphView.AXIS_X)).isNull();
        z.minX = 2.03; z.maxX = 4.47;
        z.minY = 0; z.maxY = 3.048;
        assertThat(graph.zoomRangeLine(GraphView.AXIS_X)).isEqualTo("t: 2.0 s to 4.5 s");
        assertThat(graph.zoomRangeLine(GraphView.AXIS_Y)).isEqualTo("a: 0 m/s² to 3 m/s²");
        graph.setDisplayUnit(GraphView.AXIS_Y, "foot_per_square_second");
        assertThat(graph.zoomRangeLine(GraphView.AXIS_Y)).isEqualTo("a: 0 ft/s² to 10 ft/s²");
    }
}
