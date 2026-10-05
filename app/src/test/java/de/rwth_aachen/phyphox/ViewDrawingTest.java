package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.preference.PreferenceManager;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.GeometryElement;
import de.rwth_aachen.phyphox.ExperimentView.GroupElement;
import de.rwth_aachen.phyphox.ExperimentView.ScaleElement;
import de.rwth_aachen.phyphox.SettingsActivity.SettingsFragment;

// phyphox-test: view-geometry-draw
// phyphox-test: view-scale-draw
//The drawing elements of file format 1.21 (phyphox-docs views/drawing.md): the "Drawing" view of
//corpus/generated/view-drawing.phyphox rendered on Robolectric and compared with golden images (every shape of
//geometry, both shapes of scale, the value orientations, the elements in every group and in a transform), the range
//bound to containers, and the stack's one touch: a tap on the label of an untransformed scale opens the unit dialog,
//also under a transformed needle, while a tap anywhere else on the stack does nothing. Record the goldens after an
//intentional change and review the images:
//    ./gradlew testRegularDebugUnitTest --tests '*ViewDrawingTest*' -Pphyphox.goldens=record
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class ViewDrawingTest {

    private static final String FIXTURE = "view-drawing.phyphox";
    private static final int WIDTH_DP = 411;

    private ActivityController<Experiment> controller;

    @After
    public void close() {
        if (controller != null)
            controller.close();
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit().remove(Units.Setting.PREF_KEY).commit();
    }

    private Experiment launchCorpusFixture() throws Exception {
        File corpus = CorpusTestEnvironment.findCorpus();
        assumeTrue("No phyphox-docs checkout found next to this repository - fixture skipped.", corpus != null);
        return launch(new String(Files.readAllBytes(new File(corpus, "generated/" + FIXTURE).toPath()), StandardCharsets.UTF_8));
    }

    private Experiment launch(String xml) throws Exception {
        File target = new File(RuntimeEnvironment.getApplication().getFilesDir(), "drawing-test.phyphox");
        Files.write(target.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Const.EXPERIMENT_XML, "drawing-test.phyphox");
        intent.putExtra(Const.EXPERIMENT_ISASSET, false);
        controller = Robolectric.buildActivity(Experiment.class, intent).setup();
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            PhyphoxExperiment experiment = controller.get().experiment;
            if (experiment != null && experiment.loaded && experiment.experimentViews.get(0).elements.get(0).rootView != null)
                return controller.get();
            Thread.sleep(20);
        }
        throw new AssertionError("did not load: " + (controller.get().experiment == null ? "no experiment" : controller.get().experiment.message));
    }

    private static void theme(String setting, String qualifiers) {
        RuntimeEnvironment.setQualifiers(qualifiers);
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit()
                .putString(RuntimeEnvironment.getApplication().getString(R.string.setting_dark_mode_key), setting).commit();
        SettingsFragment.setApplicationTheme(setting);
    }

    private static int windowBackground(Experiment activity) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true))
            return value.data;
        return android.graphics.Color.BLACK;
    }

    private static ExpViewElement top(Experiment activity, int i) {
        return activity.experiment.experimentViews.get(0).elements.get(i);
    }

    private static void layout(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    //Every top-level group of the "Drawing" view, in both themes, against the goldens
    private void snapshots(String configuration, String setting, String qualifiers) throws Exception {
        theme(setting, qualifiers);
        Experiment activity = launchCorpusFixture();
        activity.experiment.updateViews(0, true); //applies the transforms and reads the bound containers
        int widthPx = Math.round(WIDTH_DP * activity.getResources().getDisplayMetrics().density);
        int background = windowBackground(activity);
        List<String> findings = new ArrayList<>();
        List<ExpViewElement> elements = activity.experiment.experimentViews.get(0).elements;
        for (int i = 0; i < elements.size(); i++) {
            ExpViewElement element = elements.get(i);
            String slug = ((GroupElement) element).kind.name() + "-" + (i + 1);
            Bitmap rendered = ViewGolden.render(element.rootView, widthPx, background);
            String finding = ViewGolden.compare(rendered, "view-drawing", slug, configuration);
            if (finding != null)
                findings.add(slug + ": " + finding);
        }
        if (!findings.isEmpty())
            fail("view-drawing [" + configuration + "]\n  " + String.join("\n  ", findings));
    }

    @Test
    public void theDrawingViewMatchesTheGoldensInTheDarkTheme() throws Exception {
        snapshots("dark-phone", SettingsFragment.DARK_MODE_ON, "en-rUS-w411dp-h891dp-normal-port-night-mdpi");
    }

    @Test
    public void theDrawingViewMatchesTheGoldensInTheLightTheme() throws Exception {
        snapshots("light-phone", SettingsFragment.DARK_MODE_OFF, "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi");
    }

    @Test
    public void theBoxIsTheFullWidthAndWidthOverAspectRatioTall() throws Exception {
        Experiment activity = launchCorpusFixture();
        GroupElement vertical = (GroupElement) top(activity, 1);
        layout(vertical.rootView, 800);
        GeometryElement trough = (GeometryElement) vertical.getChildren().get(0);
        ScaleElement thermometer = (ScaleElement) vertical.getChildren().get(1);
        assertThat(trough.rootView.getWidth()).isEqualTo(800);
        assertThat(trough.rootView.getHeight()).isEqualTo(200); //aspectRatio 4
        assertThat(thermometer.rootView.getHeight()).isEqualTo(200);
        GroupElement gauge = (GroupElement) top(activity, 0);
        layout(gauge.rootView, 600);
        assertThat(gauge.getChildren().get(0).rootView.getHeight()).isEqualTo(600); //the default aspect ratio is a square
        assertThat(gauge.getChildren().get(2).rootView.getHeight()).isEqualTo(600);
    }

    @Test
    public void aBoundContainerReRangesTheTicsButNotTheBaseline() throws Exception {
        Experiment activity = launchCorpusFixture();
        GroupElement grid = (GroupElement) top(activity, 3);
        ScaleElement bound = (ScaleElement) grid.getChildren().get(2); //min from "lower" (-10), max from "upper" (250)
        activity.experiment.updateViews(0, true);
        assertThat(bound.effectiveMin()).isEqualTo(-10.0);
        assertThat(bound.effectiveMax()).isEqualTo(250.0);
        List<ScaleElement.Tic> tics = bound.computeTics(400);
        assertThat(tics.get(0).value).isEqualTo(0.0);
        assertThat(tics.get(tics.size() - 1).value).isEqualTo(250.0);
        //the positions of min and max do not move: -10 is at the start of the baseline, 250 at its end
        assertThat(tics.get(tics.size() - 1).fraction).isWithin(1e-9).of(1.0);
        assertThat((0.0 - bound.effectiveMin()) / (bound.effectiveMax() - bound.effectiveMin())).isWithin(1e-9).of(tics.get(0).fraction);

        //a new range from the experiment
        activity.experiment.getBuffer("upper").clear(false);
        activity.experiment.getBuffer("upper").append(50);
        activity.experiment.updateViews(0, true);
        assertThat(bound.effectiveMax()).isEqualTo(50.0);
        List<Double> values = new ArrayList<>();
        for (ScaleElement.Tic tic : bound.computeTics(400))
            if (tic.major)
                values.add(tic.value);
        assertThat(values).containsExactly(-10.0, 0.0, 10.0, 20.0, 30.0, 40.0, 50.0).inOrder();

        //an empty or NaN container leaves the attribute value
        activity.experiment.getBuffer("upper").clear(false);
        activity.experiment.updateViews(0, true);
        assertThat(bound.effectiveMax()).isEqualTo(100.0);
        activity.experiment.getBuffer("lower").clear(false);
        activity.experiment.getBuffer("lower").append(Double.NaN);
        activity.experiment.updateViews(0, true);
        assertThat(bound.effectiveMin()).isEqualTo(0.0);
    }

    private static void tap(View target, float x, float y) {
        long t = SystemClock.uptimeMillis();
        target.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
        target.dispatchTouchEvent(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0));
        ShadowLooper.idleMainLooper();
    }

    private static void dismissLatest() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        if (dialog != null)
            dialog.dismiss();
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void aTapOnTheLabelOfAnUntransformedScaleInAStackOpensTheUnitDialogAndNothingElseDoes() throws Exception {
        Experiment activity = launch("<phyphox xmlns=\"http://phyphox.org/xml\" version=\"1.21\" locale=\"en\">"
                + "<title>t</title><category>c</category><description>d</description>"
                + "<data-containers><container size=\"1\" init=\"0.5\">v</container></data-containers>"
                + "<views><view label=\"v\">"
                + "<stack>"
                + "<geometry shape=\"circle\" radius=\"0.48\" color=\"202020\" />"
                //the label sits at the centre, where the needle's pivot and the needle itself are drawn over it
                + "<scale shape=\"circular\" min=\"0\" max=\"10\" unit=\"@meter\" label=\"Distance\" labelPositionX=\"0.5\" labelPositionY=\"0.5\" />"
                + "<transform originX=\"0.5\" originY=\"0.5\">"
                + "<input as=\"rotate\" min=\"0\" max=\"1\" mapMin=\"0\" mapMax=\"3.14\">v</input>"
                + "<geometry shape=\"line\" startX=\"0.5\" startY=\"0.5\" endX=\"0.5\" endY=\"0.1\" lineColor=\"orange\" lineWidth=\"0.02\" />"
                + "</transform>"
                //a transformed scale with a convertible unit: never a tap target
                + "<transform><input as=\"opacity\">v</input><scale shape=\"linear\" min=\"0\" max=\"1\" unit=\"@second\" label=\"Time\" labelPositionX=\"0.5\" labelPositionY=\"0.9\" /></transform>"
                + "<geometry shape=\"circle\" radius=\"0.04\" color=\"orange\" />"
                + "</stack>"
                + "<scale shape=\"linear\" min=\"0\" max=\"1\" unit=\"@meter\" label=\"Alone\" aspectRatio=\"3\" labelPositionY=\"0.8\" />"
                + "</view></views></phyphox>");
        GroupElement stack = (GroupElement) top(activity, 0);
        GroupElement.StackLayout layout = (GroupElement.StackLayout) stack.rootView;
        layout(layout, 400);
        activity.experiment.updateViews(0, true);
        //draw once so the scales know where their labels are
        Bitmap bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        layout.draw(new android.graphics.Canvas(bitmap));

        ScaleElement.ScaleView scale = (ScaleElement.ScaleView) stack.getChildren().get(1).rootView;
        RectF label = scale.labelRect();
        assertThat(label).isNotNull();
        assertThat(label.contains(200, 200)).isTrue();
        //the topmost child under the centre is the hub, below it the needle: the tap still reaches the scale
        assertThat(layout.scaleLabelAt(200, 200)).isSameInstanceAs(scale);
        Dialog before = ShadowDialog.getLatestDialog(); //the activity's own progress dialog
        tap(layout, 200, 200);
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertThat(dialog).isNotSameInstanceAs(before);
        assertThat(dialog).isInstanceOf(androidx.appcompat.app.AlertDialog.class);
        assertThat(dialog.isShowing()).isTrue();
        dismissLatest();

        //the transformed scale's label is not offered the tap, even though it is a convertible scale
        ScaleElement.ScaleView transformed = (ScaleElement.ScaleView) ((GroupElement) stack.getChildren().get(3)).getChildren().get(0).rootView;
        RectF transformedLabel = transformed.labelRect();
        assertThat(transformedLabel).isNotNull();
        assertThat(layout.scaleLabelAt(transformedLabel.centerX(), transformedLabel.centerY())).isNull();
        boolean consumed = layout.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, transformedLabel.centerX(), transformedLabel.centerY(), 0));
        assertThat(consumed).isFalse();
        assertThat(ShadowDialog.getLatestDialog()).isSameInstanceAs(dialog); //no new dialog

        //a tap anywhere else on the stack does nothing and is left to the page
        consumed = layout.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 30, 30, 0));
        assertThat(consumed).isFalse();
        tap(layout, 30, 30);
        assertThat(ShadowDialog.getLatestDialog()).isSameInstanceAs(dialog);

        //the choice in the dialog converts the scale: 10 m become 32.8 ft, the geometry stays
        ScaleElement element = scale.getElement();
        element.setDisplayUnit("foot");
        assertThat(element.isConverted()).isTrue();
        assertThat(element.labelText()).isEqualTo("Distance (ft)");
        assertThat(element.effectiveMax()).isEqualTo(10.0);
        List<ScaleElement.Tic> tics = element.computeTics(400);
        assertThat(tics.get(tics.size() - 1).text).isEqualTo("30");
        assertThat(tics.get(tics.size() - 1).fraction).isWithin(1e-9).of(30 * 0.3048 / 10);

        //outside a stack the scale takes the tap on its label itself and leaves everything else to the page
        ScaleElement.ScaleView alone = (ScaleElement.ScaleView) top(activity, 1).rootView;
        layout(alone, 400);
        alone.draw(new android.graphics.Canvas(Bitmap.createBitmap(400, 134, Bitmap.Config.ARGB_8888)));
        RectF aloneLabel = alone.labelRect();
        assertThat(alone.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 10, 10, 0))).isFalse();
        tap(alone, aloneLabel.centerX(), aloneLabel.centerY());
        assertThat(ShadowDialog.getLatestDialog()).isNotSameInstanceAs(dialog);
        assertThat(ShadowDialog.getLatestDialog().isShowing()).isTrue();
        dismissLatest();
    }

    @Test
    public void aTextUnitHasNoTapTarget() throws Exception {
        Experiment activity = launchCorpusFixture();
        GroupElement gauge = (GroupElement) top(activity, 0);
        GroupElement.StackLayout layout = (GroupElement.StackLayout) gauge.rootView;
        layout(layout, 400);
        layout.draw(new android.graphics.Canvas(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)));
        ScaleElement.ScaleView load = (ScaleElement.ScaleView) gauge.getChildren().get(2).rootView; //unit "%": known, not convertible
        RectF label = load.labelRect();
        assertThat(label).isNotNull();
        assertThat(load.getElement().labelText()).isEqualTo("Load (%)");
        assertThat(layout.scaleLabelAt(label.centerX(), label.centerY())).isNull();
        Dialog before = ShadowDialog.getLatestDialog(); //the activity's own progress dialog
        tap(layout, label.centerX(), label.centerY());
        assertThat(ShadowDialog.getLatestDialog()).isSameInstanceAs(before);
    }
}
