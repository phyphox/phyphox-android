package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

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
import java.util.List;

import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.ExperimentView.ExpView;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.GraphElement;
import de.rwth_aachen.phyphox.ExperimentView.GroupElement;
import de.rwth_aachen.phyphox.ExperimentView.TransformElement;
import de.rwth_aachen.phyphox.ExperimentView.ValueElement;
import de.rwth_aachen.phyphox.helper.RGB;

// phyphox-test: view-groups-layout
// phyphox-test: view-stack-transform
// phyphox-test: colors-alpha
//View groups, the transform, alpha colours and the fixed plot area of file format 1.21 (phyphox-docs
//views/groups.md, graph.md, colors.md), laid out on Robolectric: the geometry the specification fixes,
//measured on the real element views of a loaded experiment. Images and the GL curve need a device
//(the T1 rows); everything here is plain view layout.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-night-mdpi")
public class ViewGroupsTest {

    private static final String HEAD = "<phyphox xmlns=\"http://phyphox.org/xml\" version=\"1.21\" locale=\"en\">"
            + "<title>t</title><category>c</category><description>d</description>"
            + "<data-containers>"
            + "<container size=\"1\" init=\"1\">show</container>"
            + "<container size=\"1\" init=\"0\">hide</container>"
            + "<container size=\"1\">angle</container>"
            + "<container size=\"1\" init=\"7\">v</container>"
            + "<container size=\"1\" init=\"0.25\">fade</container>"
            + "</data-containers><input></input><analysis></analysis><views><view label=\"v\">";
    private static final String TAIL = "</view></views></phyphox>";

    // ------------------------------------------------------------------ helpers

    private static ActivityController<Experiment> launch(String body) throws Exception {
        File target = new File(RuntimeEnvironment.getApplication().getFilesDir(), "groups-test.phyphox");
        Files.write(target.toPath(), (HEAD + body + TAIL).getBytes(StandardCharsets.UTF_8));
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Const.EXPERIMENT_XML, "groups-test.phyphox");
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

    //Lays the element's view out at the given width, as the page would
    private static void layout(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static ExpViewElement top(Experiment activity, int i) {
        return activity.experiment.experimentViews.get(0).elements.get(i);
    }

    // ------------------------------------------------------------------ view-groups-layout

    @Test
    public void horizontalSplitsTheRowByWeightAndAHiddenChildGivesUpItsSpace() throws Exception {
        ActivityController<Experiment> controller = launch(
                "<horizontal>"
                        + "<value label=\"two\" weight=\"2\"><input>v</input></value>"
                        + "<value label=\"one\"><input>v</input></value>"
                        + "<vertical><value label=\"a\"><input>v</input></value><value label=\"b\"><input>v</input></value></vertical>"
                        + "</horizontal>"
                        + "<horizontal>"
                        + "<value label=\"left\"><input>v</input></value>"
                        + "<value label=\"gone\" visibility=\"hide\"><input>v</input></value>"
                        + "<value label=\"right\"><input>v</input></value>"
                        + "</horizontal>");
        try {
            Experiment activity = controller.get();
            activity.experiment.updateViews(0, true); //applies the visibility buffers
            GroupElement row = (GroupElement) top(activity, 0);
            layout(row.rootView, 1000);
            List<ExpViewElement> children = row.getChildren();
            int wTwo = children.get(0).rootView.getWidth();
            int wOne = children.get(1).rootView.getWidth();
            int wColumn = children.get(2).rootView.getWidth();
            assertThat(wTwo).isWithin(2).of(500);
            assertThat(wOne).isWithin(2).of(250);
            assertThat(wColumn).isWithin(2).of(250);
            //the row is as tall as the two-value column, the single values sit centred in it
            int rowH = row.rootView.getHeight();
            assertThat(rowH).isEqualTo(children.get(2).rootView.getHeight());
            View one = children.get(1).rootView;
            assertThat((one.getTop() + one.getBottom()) / 2).isWithin(2).of(rowH / 2);

            GroupElement row2 = (GroupElement) top(activity, 1);
            layout(row2.rootView, 1000);
            assertThat(row2.getChildren().get(1).rootView.getVisibility()).isEqualTo(View.GONE);
            assertThat(row2.getChildren().get(0).rootView.getWidth()).isWithin(2).of(500);
            assertThat(row2.getChildren().get(2).rootView.getWidth()).isWithin(2).of(500);
        } finally {
            controller.close();
        }
    }

    @Test
    public void gridChoosesItsColumnsFromTheWidthAndFillsTheLastRowOnRequest() throws Exception {
        //maxWidth 10 text lines of 14 sp at mdpi = 140 px: one column up to 140 px, two up to 280, three up to 420
        ActivityController<Experiment> controller = launch(
                "<grid maxWidth=\"10\" fillLastRow=\"true\">"
                        + "<info label=\"a\" /><info label=\"b\" /><info label=\"c\" /><info label=\"d\" />"
                        + "</grid>"
                        + "<grid maxWidth=\"10\">"
                        + "<info label=\"a\" /><info label=\"b\" /><info label=\"c\" />"
                        + "</grid>");
        try {
            Experiment activity = controller.get();
            GroupElement fill = (GroupElement) top(activity, 0);
            GroupElement keep = (GroupElement) top(activity, 1);
            List<ExpViewElement> c = fill.getChildren();

            layout(fill.rootView, 130); //one column
            assertThat(c.get(0).rootView.getWidth()).isEqualTo(130);
            assertThat(c.get(1).rootView.getTop()).isAtLeast(c.get(0).rootView.getBottom());

            layout(fill.rootView, 400); //three columns: a b c on the first row, d alone and stretched
            assertThat(c.get(0).rootView.getWidth()).isWithin(1).of(133);
            assertThat(c.get(1).rootView.getLeft()).isWithin(1).of(133);
            assertThat(c.get(2).rootView.getTop()).isEqualTo(c.get(0).rootView.getTop());
            assertThat(c.get(3).rootView.getTop()).isAtLeast(c.get(0).rootView.getBottom());
            assertThat(c.get(3).rootView.getWidth()).isEqualTo(400);
            assertThat(fill.rootView.getHeight()).isEqualTo(c.get(3).rootView.getBottom());

            layout(keep.rootView, 280); //two columns; without fillLastRow the lone child of the last row keeps the column width
            assertThat(keep.getChildren().get(2).rootView.getWidth()).isEqualTo(140);
            assertThat(keep.getChildren().get(2).rootView.getLeft()).isEqualTo(0);
            assertThat(keep.getChildren().get(2).rootView.getTop()).isAtLeast(keep.getChildren().get(0).rootView.getBottom());

            layout(fill.rootView, 280); //two columns
            assertThat(c.get(1).rootView.getTop()).isEqualTo(c.get(0).rootView.getTop());
            assertThat(c.get(2).rootView.getTop()).isAtLeast(c.get(0).rootView.getBottom());
            assertThat(c.get(2).rootView.getWidth()).isEqualTo(140);
        } finally {
            controller.close();
        }
    }

    @Test
    public void docsFixtureLoadsAndNestsAsWritten() throws Exception {
        File corpus = CorpusTestEnvironment.findCorpus();
        assumeTrue("No phyphox-docs checkout found next to this repository - fixture skipped.", corpus != null);
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(new File(corpus, "generated/view-groups.phyphox"), activity);
        assertThat(experiment.loaded).isTrue();
        ExpView groups = experiment.experimentViews.get(0);
        assertThat(groups.elements).hasSize(3);
        GroupElement horizontal = (GroupElement) groups.elements.get(0);
        assertThat(horizontal.kind).isEqualTo(GroupElement.Kind.horizontal);
        assertThat(horizontal.getChildren().get(0).weight).isEqualTo(2.0);
        assertThat(horizontal.getChildren().get(1).weight).isEqualTo(1.0);
        assertThat(horizontal.label).isEmpty(); //label has no effect on a group
        GroupElement grid = (GroupElement) groups.elements.get(2);
        assertThat(grid.getMaxWidth()).isEqualTo(25.0);
        assertThat(grid.getFillLastRow()).isTrue();
        assertThat(((GroupElement) grid.getChildren().get(3)).kind).isEqualTo(GroupElement.Kind.grid);
        //every element, groups and leaves, in document order
        assertThat(groups.flatElements()).hasSize(3 + 3 + 2 + 4 + 2 + 2);

        ExpView stack = experiment.experimentViews.get(1);
        GroupElement gauge = (GroupElement) ((GroupElement) stack.elements.get(0)).getChildren().get(0);
        assertThat(gauge.kind).isEqualTo(GroupElement.Kind.stack);
        TransformElement needle = (TransformElement) gauge.getChildren().get(1);
        assertThat(needle.getOriginY()).isEqualTo(0.8);
        assertThat(needle.getBindings()).hasSize(2);
        assertThat(needle.getBindings().get(0).as).isEqualTo(TransformElement.Property.rotate);
        assertThat(needle.getBindings().get(0).clamp).isTrue();
        TransformElement marker = (TransformElement) gauge.getChildren().get(2);
        assertThat(marker.getBindings().get(2).input.isBuffer).isFalse();
        assertThat(marker.getBindings().get(2).input.getValue()).isEqualTo(0.1);
        //images inside groups are listed as resources, so /res serves them
        assertThat(experiment.resources).containsAtLeast("gauge-face.png", "gauge-needle.png", "marker.png", "campus-map.png");
        GraphElement overlay = (GraphElement) ((TransformElement) ((GroupElement) ((GroupElement) stack.elements.get(0)).getChildren().get(1)).getChildren().get(2)).getChild();
        assertThat(overlay.hasFixedPlotArea()).isTrue();
    }

    // ------------------------------------------------------------------ view-stack-transform

    @Test
    public void stackSharesOneRectangleAndTheTransformFollowsItsContainers() throws Exception {
        ActivityController<Experiment> controller = launch(
                "<stack>"
                        + "<separator height=\"6\" color=\"39a2ff40\" />"
                        + "<transform originX=\"0.5\" originY=\"0.8\">"
                        + "<input as=\"rotate\" min=\"0\" max=\"360\" mapMin=\"0\" mapMax=\"6.2832\" clamp=\"true\">angle</input>"
                        + "<input as=\"opacity\">fade</input>"
                        + "<info label=\"needle\" />"
                        + "</transform>"
                        + "<transform>"
                        + "<input as=\"scale\" type=\"value\">0.5</input>"
                        + "<input as=\"translateX\" type=\"value\">0.25</input>"
                        + "<value label=\"\" unit=\"%\"><input>v</input></value>"
                        + "</transform>"
                        + "</stack>");
        try {
            Experiment activity = controller.get();
            GroupElement stack = (GroupElement) top(activity, 0);
            assertThat(stack.rootView).isInstanceOf(GroupElement.StackLayout.class);
            layout(stack.rootView, 600);
            List<ExpViewElement> layers = stack.getChildren();
            int tallest = 0;
            for (ExpViewElement layer : layers) {
                assertThat(layer.rootView.getLeft()).isEqualTo(0);
                assertThat(layer.rootView.getWidth()).isEqualTo(600);
                tallest = Math.max(tallest, layer.rootView.getHeight());
            }
            assertThat(stack.rootView.getHeight()).isEqualTo(tallest);
            assertThat(layers.get(0).rootView.getHeight()).isEqualTo(tallest); //the separator is the tallest child
            View needle = layers.get(1).rootView;
            assertThat((needle.getTop() + needle.getBottom()) / 2).isWithin(1).of(tallest / 2);
            //document order is the z-order: later children lie above earlier ones
            assertThat(((ViewGroup) stack.rootView).indexOfChild(layers.get(2).rootView)).isGreaterThan(((ViewGroup) stack.rootView).indexOfChild(layers.get(0).rootView));
            //a stack is not interactive
            assertThat(((GroupElement.StackLayout) stack.rootView).onInterceptTouchEvent(null)).isTrue();

            TransformElement rotating = (TransformElement) layers.get(1);
            TransformElement constant = (TransformElement) layers.get(2);
            PhyphoxExperiment experiment = activity.experiment;
            experiment.updateViews(0, true);
            //an empty container leaves the neutral value, the opacity follows fade
            assertThat(needle.getRotation()).isEqualTo(0f);
            assertThat(needle.getAlpha()).isWithin(1e-6f).of(0.25f);
            assertThat(needle.getPivotX()).isWithin(0.5f).of(300f);
            assertThat(needle.getPivotY()).isWithin(0.5f).of(needle.getHeight() * 0.8f);
            //the constant bindings: scale about the centre, shift by a quarter of the width
            View marker = constant.rootView;
            assertThat(marker.getScaleX()).isWithin(1e-6f).of(0.5f);
            assertThat(marker.getScaleY()).isWithin(1e-6f).of(0.5f);
            assertThat(marker.getTranslationX()).isWithin(0.5f).of(150f);

            //90 of 0..360 maps to pi/2, i.e. 90 degrees clockwise
            experiment.getBuffer("angle").append(90);
            experiment.updateViews(0, true);
            assertThat(needle.getRotation()).isWithin(0.01f).of(90f);
            //outside the range the clamp holds the end of the map
            experiment.getBuffer("angle").clear(false);
            experiment.getBuffer("angle").append(540);
            experiment.updateViews(0, true);
            assertThat(needle.getRotation()).isWithin(0.01f).of(360f);
            //NaN is neutral again
            experiment.getBuffer("angle").clear(false);
            experiment.getBuffer("angle").append(Double.NaN);
            experiment.updateViews(0, true);
            assertThat(needle.getRotation()).isEqualTo(0f);
            float[] applied = rotating.appliedTransform();
            assertThat(applied[2]).isEqualTo(0f);
        } finally {
            controller.close();
        }
    }

    @Test
    public void graphInAStackIsStaticAndAFixedPlotAreaPinsThePlot() throws Exception {
        ActivityController<Experiment> controller = launch(
                "<stack>"
                        + "<separator height=\"1\" />"
                        + "<graph label=\"Overlay\" plotLeft=\"0.1\" plotBottom=\"0.9\"><input axis=\"x\">v</input><input axis=\"y\">v</input></graph>"
                        + "</stack>"
                        + "<graph label=\"Plain\"><input axis=\"x\">v</input><input axis=\"y\">v</input></graph>");
        try {
            Experiment activity = controller.get();
            GroupElement stack = (GroupElement) top(activity, 0);
            GraphElement overlay = (GraphElement) stack.getChildren().get(1);
            GraphElement plain = (GraphElement) top(activity, 1);
            assertThat(overlay.hasFixedPlotArea()).isTrue();
            assertThat(plain.hasFixedPlotArea()).isFalse();
            //no tap-to-maximize inside a stack, and maximize() is a no-op there
            assertThat(overlay.rootView.hasOnClickListeners()).isFalse();
            assertThat(plain.rootView.hasOnClickListeners()).isTrue();
            overlay.maximize();
            assertThat(overlay.state).isEqualTo(ExpView.State.normal);
            //the web config carries the plot area
            assertThat(overlay.getWebGraphConfig()).contains("\"plotLeft\":0.1");
            assertThat(overlay.getWebGraphConfig()).contains("\"plotTop\":null");
            assertThat(plain.getWebGraphConfig()).contains("\"plotLeft\":null");
        } finally {
            controller.close();
        }
    }

    // ------------------------------------------------------------------ colors-alpha

    @Test
    public void eightDigitColorsCarryTheirAlphaEverywhere() throws Exception {
        android.content.res.Resources res = ApplicationProvider.getApplicationContext().getResources();
        RGB alpha = RGB.fromPhyphoxStringStrict("ff7e2280", res);
        assertThat(alpha.a()).isEqualTo(0x80);
        assertThat(alpha.r()).isEqualTo(0xff);
        assertThat(alpha.g()).isEqualTo(0x7e);
        assertThat(alpha.b()).isEqualTo(0x22);
        assertThat(alpha.intColor()).isEqualTo(0x80ff7e22);
        assertThat(alpha.hexString()).isEqualTo("ff7e2280");
        RGB opaque = RGB.fromPhyphoxStringStrict("#ff7e22", res);
        assertThat(opaque.a()).isEqualTo(0xff);
        assertThat(opaque.hexString()).isEqualTo("ff7e22");
        assertThat(RGB.fromPhyphoxStringStrict("ff7e2", res)).isNull();
        assertThat(RGB.fromPhyphoxStringStrict("ff7e22800", res)).isNull();
        //the light-mode adjustment keeps the alpha byte (colors.md)
        RGB adjusted = RGB.fromPhyphoxStringStrict("e7e09b40", res).adjustedColorForLightTheme(res);
        assertThat(adjusted.a()).isEqualTo(0x40);
        assertThat(adjusted.hexString()).endsWith("40");
        assertThat(adjusted.r() + adjusted.g() + adjusted.b()).isNotEqualTo(0xe7 + 0xe0 + 0x9b);

        ActivityController<Experiment> controller = launch(
                "<value label=\"a\" color=\"ff7e2280\"><input>v</input></value>"
                        + "<separator height=\"1\" color=\"39a2ff40\" />"
                        + "<graph label=\"g\" color=\"#2bfb4cc0\" mapColor1=\"0000ff00\" mapColor2=\"ff0000c0\"><input axis=\"x\">v</input><input axis=\"y\" color=\"edf668ff\">v</input></graph>"
                        + "<value label=\"b\" color=\"ff7e22\"><input>v</input></value>");
        try {
            Experiment activity = controller.get();
            ValueElement value = (ValueElement) top(activity, 0);
            assertThat(value.getViewHTML(0)).contains("color:#ff7e2280");
            assertThat(((ValueElement) top(activity, 3)).getViewHTML(3)).contains("color:#ff7e22\"");
            View separator = top(activity, 1).rootView;
            assertThat(((ColorDrawable) separator.getBackground()).getColor()).isEqualTo(0x4039a2ff);
            GraphElement graph = (GraphElement) top(activity, 2);
            String cfg = graph.getWebGraphConfig();
            assertThat(cfg).contains("\"color\":\"#edf668\""); //an explicit ff alpha stays six digits
            assertThat(cfg).contains("\"colorScale\":[\"#0000ff00\",\"#ff0000c0\"]");
            assertThat(top(activity, 1).getViewHTML(1)).contains("background: #39a2ff40");
        } finally {
            controller.close();
        }
    }
}
