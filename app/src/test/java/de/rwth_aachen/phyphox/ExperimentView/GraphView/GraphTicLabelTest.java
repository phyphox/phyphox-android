package de.rwth_aachen.phyphox.ExperimentView.GraphView;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
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

import de.rwth_aachen.phyphox.helper.Helper;
import de.rwth_aachen.phyphox.FloatBufferRepresentation;

// phyphox-test: graph-tic-labels
//The tic labels of a fixed -2..10 / -1..6 axis pair, whose outer tics sit right on the plot border: x labels are
//centred on their tic (the first frame used to draw them left-aligned), and a label at the border is moved inside
//the view instead of being clipped by it.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class GraphTicLabelTest {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 300;

    private GraphView graph;
    private Bitmap bitmap;
    private float textSize;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        graph = new GraphView(context, new PlotAreaView(context), new PlotRenderer(context));
        graph.setCurves(1);
        graph.setLabel("x", "y", null, null, null, null, null);
        graph.setScaleModeX(GraphView.ScaleMode.fixed, -2, GraphView.ScaleMode.fixed, 10);
        graph.setScaleModeY(GraphView.ScaleMode.fixed, -1, GraphView.ScaleMode.fixed, 6);
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
        FloatBuffer data = ByteBuffer.allocateDirect(3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        data.put(new float[]{1, 2, 3});
        FloatBufferRepresentation values = new FloatBufferRepresentation(data, 0, 3);
        graph.addGraphData(new FloatBufferRepresentation[]{values}, 1, 3, new FloatBufferRepresentation[]{values}, 1, 3,
                Double.NaN, Double.NaN, new List[1], new List[1]);
        textSize = Helper.getUserSelectedGraphSetting(context, Helper.GraphField.TEXT_SIZE);

        bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        graph.draw(canvas); //the first frame, where the alignment bug lived
    }

    private float measure(String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        return paint.measureText(text);
    }

    //Bounding box of the non-white pixels in a region, or null
    private Rect ink(int left, int top, int right, int bottom) {
        Rect box = null;
        for (int y = Math.max(0, top); y < Math.min(HEIGHT, bottom); y++)
            for (int x = Math.max(0, left); x < Math.min(WIDTH, right); x++)
                if (bitmap.getPixel(x, y) != Color.WHITE) {
                    if (box == null)
                        box = new Rect(x, y, x + 1, y + 1);
                    else
                        box.union(x, y, x + 1, y + 1);
                }
        return box;
    }

    //The band below the plot that holds the x tic labels: between the frame and the axis label
    private int xLabelTop() {
        return graph.graphSetup.plotBoundT + graph.graphSetup.plotBoundH + 3;
    }

    private int xLabelBottom() {
        return xLabelTop() + (int) (textSize * 1.2f);
    }

    @Test
    public void xTicLabelsAreCentredOnTheirTic() {
        int tic = (int) Math.round(graph.dataXToViewX(5));
        Rect label = ink(tic - 40, xLabelTop(), tic + 40, xLabelBottom());
        assertThat(label).isNotNull();
        assertThat(label.centerX()).isWithin(2).of(tic);
    }

    @Test
    public void aLabelOnTheRightBorderIsMovedInsideTheView() {
        int right = graph.graphSetup.plotBoundL + graph.graphSetup.plotBoundW;
        Rect label = ink(right - 60, xLabelTop(), WIDTH, xLabelBottom());
        assertThat(label).isNotNull();
        //the whole "10" is there, ending inside the view
        assertThat(label.width()).isAtLeast((int) (measure("10") * 0.8f));
        assertThat(label.right).isAtMost(WIDTH); //Rect.right is exclusive
    }

    @Test
    public void aLabelOnTheTopBorderIsMovedInsideTheView() {
        int left = graph.graphSetup.plotBoundL;
        Rect label = ink(0, 0, left - 1, (int) (textSize * 1.5f));
        assertThat(label).isNotNull();
        //the whole "6" is there, starting inside the view
        assertThat(label.top).isAtLeast(0);
        assertThat(label.height()).isAtLeast((int) (textSize * 0.6f));
    }
}
