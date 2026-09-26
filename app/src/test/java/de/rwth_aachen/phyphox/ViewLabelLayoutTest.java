package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.GroupElement;

// phyphox-test: view-vertical-layout
// phyphox-test: grid-screen-unit
//Labels in narrow columns and the grid's screen unit (file format 1.21, phyphox-docs views/groups.md,
//"Labels in narrow columns" and "View-Element: grid"): verticalLayout puts the label above the control,
//both full width and left-aligned; without a label the control takes the whole row; an info without a
//label keeps one line of height and a button its size; a grid with maxWidthUnit="screen" counts its
//columns in multiples of the window's shorter side, so a phone shows one column in portrait and more
//in landscape.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-night-mdpi")
public class ViewLabelLayoutTest {

    private static final String HEAD = "<phyphox xmlns=\"http://phyphox.org/xml\" version=\"1.21\" locale=\"en\">"
            + "<title>t</title><category>c</category><description>d</description>"
            + "<data-containers><container size=\"1\" init=\"7\">v</container><container size=\"1\" init=\"1\">run</container></data-containers>"
            + "<input></input><analysis></analysis><views><view label=\"v\">";
    private static final String TAIL = "</view></views></phyphox>";

    private static ActivityController<Experiment> launch(String body) throws Exception {
        File target = new File(RuntimeEnvironment.getApplication().getFilesDir(), "labels-test.phyphox");
        Files.write(target.toPath(), (HEAD + body + TAIL).getBytes(StandardCharsets.UTF_8));
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Const.EXPERIMENT_XML, "labels-test.phyphox");
        intent.putExtra(Const.EXPERIMENT_ISASSET, false);
        ActivityController<Experiment> controller = Robolectric.buildActivity(Experiment.class, intent).setup();
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            PhyphoxExperiment experiment = controller.get().experiment;
            if (experiment != null && experiment.loaded && experiment.experimentViews.get(0).elements.get(0).rootView != null)
                return controller;
            Thread.sleep(20);
        }
        throw new AssertionError("did not load: " + (controller.get().experiment == null ? "no experiment" : controller.get().experiment.message));
    }

    private static void layout(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static ExpViewElement top(Experiment activity, int i) {
        return activity.experiment.experimentViews.get(0).elements.get(i);
    }

    //The row that holds label and control: the element's root, or for the slider its first row
    private static LinearLayout labelRow(ExpViewElement element) {
        LinearLayout root = (LinearLayout) element.rootView;
        if (root.getChildCount() > 0 && root.getChildAt(0) instanceof LinearLayout && ((LinearLayout) root.getChildAt(0)).getChildCount() > 0
                && ((LinearLayout) root.getChildAt(0)).getChildAt(0) instanceof TextView && element.getClass().getSimpleName().equals("SliderElement"))
            return (LinearLayout) root.getChildAt(0);
        return root;
    }

    @Test
    public void verticalLayoutStacksLabelAboveTheControlLeftAligned() throws Exception {
        ActivityController<Experiment> controller = launch(
                "<value label=\"Frequency\" unit=\"Hz\" verticalLayout=\"true\"><input>v</input></value>"
                        + "<edit label=\"Length\" unit=\"m\" verticalLayout=\"true\"><output>v</output></edit>"
                        + "<toggle label=\"Run\" verticalLayout=\"true\"><output>run</output></toggle>"
                        + "<dropdown label=\"Choice\" verticalLayout=\"true\"><map value=\"1\">one</map><map value=\"2\">two</map><output>v</output></dropdown>"
                        + "<slider label=\"Level\" minValue=\"0\" maxValue=\"10\" showValue=\"true\" verticalLayout=\"true\"><output>v</output></slider>"
                        + "<value label=\"Default\" unit=\"Hz\"><input>v</input></value>");
        try {
            Experiment activity = controller.get();
            for (int i = 0; i < 5; i++) {
                ExpViewElement element = top(activity, i);
                layout(element.rootView, 600);
                LinearLayout row = i == 4 ? (LinearLayout) ((LinearLayout) element.rootView).getChildAt(0) : (LinearLayout) element.rootView;
                assertThat(row.getOrientation()).isEqualTo(LinearLayout.VERTICAL);
                TextView label = (TextView) row.getChildAt(0);
                View control = row.getChildAt(1);
                assertThat(label.getWidth()).isEqualTo(600);
                assertThat(control.getWidth()).isEqualTo(600);
                assertThat(control.getTop()).isAtLeast(label.getBottom());
                assertThat(Gravity.getAbsoluteGravity(label.getGravity(), View.LAYOUT_DIRECTION_LTR) & Gravity.HORIZONTAL_GRAVITY_MASK).isEqualTo(Gravity.LEFT);
                assertThat(label.getPaddingLeft()).isEqualTo(0);
            }
            //the slider: label, value and the slider in three rows
            LinearLayout slider = (LinearLayout) top(activity, 4).rootView;
            assertThat(slider.getChildAt(1).getTop()).isAtLeast(slider.getChildAt(0).getBottom());
            //the default stays side by side, label right-aligned in the left half
            ExpViewElement plain = top(activity, 5);
            layout(plain.rootView, 600);
            LinearLayout row = (LinearLayout) plain.rootView;
            assertThat(row.getOrientation()).isEqualTo(LinearLayout.HORIZONTAL);
            assertThat(row.getChildAt(0).getWidth()).isEqualTo(300);
            assertThat(Gravity.getAbsoluteGravity(((TextView) row.getChildAt(0)).getGravity(), View.LAYOUT_DIRECTION_LTR) & Gravity.HORIZONTAL_GRAVITY_MASK).isEqualTo(Gravity.RIGHT);
        } finally {
            controller.close();
        }
    }

    @Test
    public void withoutALabelTheControlTakesTheRowAndInfoAndButtonKeepTheirSize() throws Exception {
        ActivityController<Experiment> controller = launch(
                "<value unit=\"Hz\"><input>v</input></value>"
                        + "<edit unit=\"m\"><output>v</output></edit>"
                        + "<toggle><output>run</output></toggle>"
                        + "<dropdown><map value=\"1\">one</map><map value=\"2\">two</map><output>v</output></dropdown>"
                        + "<slider label=\"\" minValue=\"0\" maxValue=\"10\" showValue=\"true\"><output>v</output></slider>"
                        + "<graph><input axis=\"x\">v</input><input axis=\"y\">v</input></graph>"
                        + "<info />"
                        + "<info label=\"one line\" />"
                        + "<button><input type=\"value\">1</input><output>run</output></button>"
                        + "<button label=\"Start\"><input type=\"value\">1</input><output>run</output></button>"
                        + "<value label=\"With\" unit=\"Hz\" verticalLayout=\"true\"><input>v</input></value>");
        try {
            Experiment activity = controller.get();
            for (int i = 0; i < 5; i++) {
                ExpViewElement element = top(activity, i);
                assertThat(element.hasLabel()).isFalse();
                layout(element.rootView, 600);
                LinearLayout row = i == 4 ? (LinearLayout) ((LinearLayout) element.rootView).getChildAt(0) : (LinearLayout) element.rootView;
                assertThat(row.getChildCount()).isEqualTo(1); //no label view at all
                assertThat(row.getChildAt(0).getWidth()).isEqualTo(600);
                assertThat(row.getChildAt(0).getLeft()).isEqualTo(0);
                assertThat(row.getChildAt(0).getPaddingLeft()).isEqualTo(0);
            }
            //graph: no title row, the frame starts at the top
            View graph = top(activity, 5).rootView;
            layout(graph, 600);
            View frame = graph.findViewById(R.id.graph_frame);
            assertThat(graph.findViewById(R.id.graph_label).getVisibility()).isEqualTo(View.GONE);
            assertThat(graph.findViewById(R.id.graph_expand_image).getVisibility()).isEqualTo(View.GONE);
            assertThat(((ViewGroup.MarginLayoutParams) frame.getLayoutParams()).topMargin).isEqualTo(0);
            //info: an empty one keeps the height of the one-line info next to it
            View emptyInfo = top(activity, 6).rootView;
            View info = top(activity, 7).rootView;
            layout(emptyInfo, 600);
            layout(info, 600);
            assertThat(emptyInfo.getHeight()).isEqualTo(info.getHeight());
            //button: the same size with an empty caption
            View emptyButton = top(activity, 8).rootView;
            View button = top(activity, 9).rootView;
            layout(emptyButton, 600);
            layout(button, 600);
            assertThat(emptyButton.getHeight()).isEqualTo(button.getHeight());
            //the markup follows: no label span without a label, a verticalLayout class with one
            assertThat(top(activity, 0).getViewHTML(0)).doesNotContain("class=\"label\"");
            assertThat(top(activity, 2).getViewHTML(2)).doesNotContain("class=\"label\"");
            assertThat(top(activity, 10).getViewHTML(10)).contains("valueElement adjustableColor verticalLayout");
            assertThat(top(activity, 10).getViewHTML(10)).contains("<span class=\"label\">With</span>");
        } finally {
            controller.close();
        }
    }

    @Test
    public void gridScreenUnitCountsColumnsInWindowShorterSides() throws Exception {
        //A phone window of 411 x 891 px (the qualifiers): maxWidth 1 screen = 411 px
        ActivityController<Experiment> controller = launch(
                "<grid maxWidth=\"1\" maxWidthUnit=\"screen\">"
                        + "<info label=\"a\" /><info label=\"b\" /><info label=\"c\" /><info label=\"d\" />"
                        + "</grid>"
                        + "<grid maxWidth=\"0.5\" maxWidthUnit=\"SCREEN\">"
                        + "<info label=\"a\" /><info label=\"b\" />"
                        + "</grid>");
        try {
            Experiment activity = controller.get();
            GroupElement grid = (GroupElement) top(activity, 0);
            assertThat(grid.getMaxWidthScreenUnit()).isTrue();
            GroupElement.GridGroupLayout layout = (GroupElement.GridGroupLayout) grid.rootView;
            assertThat(layout.maxWidthPx()).isWithin(0.5f).of(411f);
            //portrait: the available width is the shorter side -> one column
            layout(grid.rootView, 411);
            assertThat(grid.getChildren().get(1).rootView.getTop()).isAtLeast(grid.getChildren().get(0).rootView.getBottom());
            //landscape: the width is the long side, the unit stays the shorter one -> ceil(891 / 411) = 3 columns
            layout(grid.rootView, 891);
            assertThat(layout.columnsFor(891)).isEqualTo(3);
            assertThat(grid.getChildren().get(2).rootView.getTop()).isEqualTo(grid.getChildren().get(0).rootView.getTop());
            assertThat(grid.getChildren().get(3).rootView.getTop()).isAtLeast(grid.getChildren().get(0).rootView.getBottom());
            //the enum matches case-insensitively; half a screen gives two columns in portrait
            GroupElement half = (GroupElement) top(activity, 1);
            layout(half.rootView, 411);
            assertThat(half.getChildren().get(1).rootView.getTop()).isEqualTo(half.getChildren().get(0).rootView.getTop());
            assertThat(half.getChildren().get(1).rootView.getLeft()).isWithin(1).of(205);
        } finally {
            controller.close();
        }
    }

    @Test
    public void gridTextUnitIsTheDefault() throws Exception {
        ActivityController<Experiment> controller = launch(
                "<grid maxWidth=\"10\" maxWidthUnit=\"text\"><info label=\"a\" /></grid>"
                        + "<grid maxWidth=\"10\"><info label=\"a\" /></grid>");
        try {
            Experiment activity = controller.get();
            assertThat(((GroupElement) top(activity, 0)).getMaxWidthScreenUnit()).isFalse();
            assertThat(((GroupElement) top(activity, 1)).getMaxWidthScreenUnit()).isFalse();
            assertThat(((GroupElement.GridGroupLayout) top(activity, 1).rootView).maxWidthPx()).isWithin(0.5f).of(140f); //10 lines of 14 sp at mdpi
        } finally {
            controller.close();
        }
    }
}
