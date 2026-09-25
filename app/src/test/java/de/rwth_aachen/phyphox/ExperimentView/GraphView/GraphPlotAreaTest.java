package de.rwth_aachen.phyphox.ExperimentView.GraphView;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

// phyphox-test: view-stack-transform
//The fixed plot area of file format 1.21 (graph.md, "Fixing the plot area"): plotLeft & co. pin the plot
//rectangle to fractions of the view, unset edges default to the view's edges. GraphView is a plain canvas
//View, so the layout it reports to the renderer can be checked without a GL surface.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class GraphPlotAreaTest {

    @Test
    public void graphViewLaysThePlotOutAtTheGivenFractions() {
        Context context = ApplicationProvider.getApplicationContext();
        GraphView graph = new GraphView(context, new PlotAreaView(context), new PlotRenderer(context));
        FrameLayout parent = new FrameLayout(context);
        parent.addView(graph);
        graph.setCurves(1);
        graph.setPointInfoListener(new GraphView.PointInfo() {
            @Override
            public void showPointInfo(float viewX, float viewY, float pointX, float pointY, float pointZ, int index) {}

            @Override
            public void hidePointInfo(int index) {}
        });
        graph.setLabel("x", "y", null, "s", "m", null, null);
        parent.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 600, 400);
        Canvas canvas = new Canvas(Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888));

        graph.draw(canvas);
        int[] auto = graph.plotBounds();
        assertThat(auto[0]).isGreaterThan(0); //room for the y tic labels
        assertThat(auto[0] + auto[2]).isEqualTo(600);
        assertThat(graph.hasFixedPlotArea()).isFalse();

        graph.setPlotArea(0.1, Double.NaN, Double.NaN, 0.9);
        graph.draw(canvas);
        assertThat(graph.hasFixedPlotArea()).isTrue();
        assertThat(graph.plotBounds()).isEqualTo(new int[]{60, 0, 540, 360});

        graph.setPlotArea(0.25, 0.25, 0.75, 0.75);
        graph.draw(canvas);
        assertThat(graph.plotBounds()).isEqualTo(new int[]{150, 100, 300, 200});
    }
}
