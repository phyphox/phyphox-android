package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

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
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.ExperimentView.EditElement;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.GraphElement;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.GraphView;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.InteractiveGraphView;
import de.rwth_aachen.phyphox.ExperimentView.ValueElement;

// phyphox-test: unit-conversion-display
//What the elements DISPLAY follows the unit conversion (phyphox-docs docs/file-format/units.md, "Conversion in the
//app"), checked on corpus/generated/unit-references.phyphox under each Unit system setting and after the switch the
//unit dialog makes: the value's number and unit with the precision rule, the affine temperature, the edit field's
//value, unit and limits with a typed value converted back, the graph's axis titles, tic labels and the picker's
//point, difference and slope read-outs with the composed slope unit; text units, positiveUnit and decimal="false"
//stay untouched; the deprecated placeholder behaves like the reference; the buffers never change.
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class UnitConversionDisplayTest {

    private ActivityController<Experiment> controller;

    @After
    public void close() {
        if (controller != null)
            controller.close();
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit().remove(Units.Setting.PREF_KEY).commit();
    }

    private static void setting(String value) {
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit().putString(Units.Setting.PREF_KEY, value).commit();
    }

    private Experiment launchCorpusFixture() throws Exception {
        File corpus = CorpusTestEnvironment.findCorpus();
        assumeTrue("No phyphox-docs checkout found next to this repository - fixture skipped.", corpus != null);
        return launch(new String(Files.readAllBytes(new File(corpus, "generated/unit-references.phyphox").toPath()), StandardCharsets.UTF_8));
    }

    private Experiment launch(String xml) throws Exception {
        File target = new File(RuntimeEnvironment.getApplication().getFilesDir(), "units-test.phyphox");
        Files.write(target.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Const.EXPERIMENT_XML, "units-test.phyphox");
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

    private static <T extends ExpViewElement> T element(Experiment activity, int i, Class<T> type) {
        return type.cast(activity.experiment.experimentViews.get(0).elements.get(i));
    }

    //Renders the element from its buffer, as the main loop does every 40 ms
    private static String shown(Experiment activity, ValueElement value) {
        value.notifyUpdate(false, false);
        value.onMayReadFromBuffers(activity.experiment);
        return value.displayedText().toString();
    }

    private static String shown(Experiment activity, EditElement edit) {
        edit.onMayReadFromBuffers(activity.experiment);
        return edit.displayedText().toString();
    }

    //The edit box: the row holds the label and a layout with the box and its unit
    private static EditText field(EditElement edit) {
        return (EditText) ((LinearLayout) ((LinearLayout) edit.rootView).getChildAt(1)).getChildAt(0);
    }

    private static void type(EditElement edit, String text) {
        field(edit).requestFocus();
        field(edit).setText(text);
        field(edit).clearFocus();
    }

    private static GraphView graphView(GraphElement graph) {
        return ((InteractiveGraphView) graph.rootView).graphView;
    }

    //Draws the plot the way onDraw does, so the tic labels of the frame are available. A frame without touch picks
    //clears the pick markers, so read-outs are checked with markers set after the last draw.
    private static void draw(GraphView gv) {
        gv.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        gv.layout(0, 0, 600, 300);
        gv.draw(new Canvas(Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)));
    }

    private static String readOut(InteractiveGraphView igv) {
        return igv.popupWindowText == null ? null : igv.popupWindowText.getText().toString();
    }

    private static double[] buffers(Experiment activity) {
        return new double[]{activity.experiment.getBuffer("distance").value, activity.experiment.getBuffer("temperature").value, activity.experiment.getBuffer("length").value};
    }

    @Test
    public void experimentSettingShowsTheUnitsAsTheFileNamesThem() throws Exception {
        setting("experiment");
        Experiment activity = launchCorpusFixture();
        double[] before = buffers(activity);
        ValueElement distance = element(activity, 0, ValueElement.class);
        assertThat(shown(activity, distance)).isEqualTo("150.0 cm");
        assertThat(shown(activity, element(activity, 1, ValueElement.class))).isEqualTo("21.5 °C");
        ValueElement jerk = element(activity, 2, ValueElement.class);
        assertThat(jerk.isConvertible()).isFalse();
        assertThat(jerk.getUnit().text).isEqualTo("m/s³");
        EditElement length = element(activity, 3, EditElement.class);
        assertThat(shown(activity, length)).isEqualTo("0.5");
        assertThat(length.displayUnitSymbol()).isEqualTo("m");
        assertThat(length.displayedLimits()[0]).isWithin(1e-9).of(0.1);
        assertThat(length.displayedLimits()[1]).isWithin(1e-9).of(2.0);
        GraphView acceleration = graphView(element(activity, 4, GraphElement.class));
        assertThat(acceleration.getLabelAndUnitX()).isEqualTo("t (s)");
        assertThat(acceleration.getLabelAndUnitY()).isEqualTo("a (m/s²)");
        GraphView map = graphView(element(activity, 6, GraphElement.class));
        assertThat(map.getLabelAndUnitZ()).isEqualTo("B (µT)");
        assertThat(buffers(activity)).isEqualTo(before);
    }

    @Test
    public void imperialSettingConvertsEveryConvertibleElementAndSkipsTheRest() throws Exception {
        setting("imperial");
        Experiment activity = launchCorpusFixture();
        double[] before = buffers(activity);
        //cm with one decimal shows two in inches (the precision rule)
        assertThat(shown(activity, element(activity, 0, ValueElement.class))).isEqualTo("59.06 in");
        assertThat(shown(activity, element(activity, 1, ValueElement.class))).isEqualTo("70.7 °F");
        assertThat(element(activity, 2, ValueElement.class).getDisplayUnitId()).isNull(); //text stays text
        EditElement length = element(activity, 3, EditElement.class);
        assertThat(length.getDisplayUnitId()).isEqualTo("foot");
        assertThat(shown(activity, length)).isEqualTo("1.64042");
        assertThat(length.displayUnitSymbol()).isEqualTo("ft");
        assertThat(length.displayedLimits()[0]).isWithin(1e-3).of(0.328);
        assertThat(length.displayedLimits()[1]).isWithin(1e-3).of(6.562);
        GraphView acceleration = graphView(element(activity, 4, GraphElement.class));
        assertThat(acceleration.getLabelAndUnitX()).isEqualTo("t (s)"); //a common unit stays
        assertThat(acceleration.getLabelAndUnitY()).isEqualTo("a (ft/s²)");
        //the deprecated placeholder behaves exactly like the reference
        GraphView deprecated = graphView(element(activity, 5, GraphElement.class));
        assertThat(deprecated.getUnitId(GraphView.AXIS_X)).isEqualTo("second");
        assertThat(deprecated.getLabelAndUnitY()).isEqualTo("a (ft/s²)");
        GraphView map = graphView(element(activity, 6, GraphElement.class));
        assertThat(map.getLabelAndUnitX()).isEqualTo("x (in)");
        assertThat(map.getLabelAndUnitZ()).isEqualTo("B (µT)"); //no counterpart
        assertThat(buffers(activity)).isEqualTo(before);
    }

    @Test
    public void metricSettingLeavesMetricUnitsAlone() throws Exception {
        setting("metric");
        Experiment activity = launchCorpusFixture();
        assertThat(shown(activity, element(activity, 0, ValueElement.class))).isEqualTo("150.0 cm");
        assertThat(shown(activity, element(activity, 1, ValueElement.class))).isEqualTo("21.5 °C");
        assertThat(graphView(element(activity, 4, GraphElement.class)).getLabelAndUnitY()).isEqualTo("a (m/s²)");
    }

    @Test
    public void aSwitchedUnitChangesWhatTheElementShowsButNotItsData() throws Exception {
        setting("experiment");
        Experiment activity = launchCorpusFixture();
        double[] before = buffers(activity);

        ValueElement distance = element(activity, 0, ValueElement.class);
        distance.setDisplayUnit("meter"); //what the dialog does
        assertThat(shown(activity, distance)).isEqualTo("1.500 m");
        distance.setDisplayUnit("inch");
        assertThat(shown(activity, distance)).isEqualTo("59.06 in");
        distance.setDisplayUnit("second"); //another quantity is refused
        assertThat(shown(activity, distance)).isEqualTo("59.06 in");

        ValueElement temperature = element(activity, 1, ValueElement.class);
        temperature.setDisplayUnit("kelvin");
        //294.65 is a tie: Java's formatter rounds the shortest decimal form half up ("294.7"), a C printf rounds the
        //binary value ("294.6"); a platform difference of number formatting, not of the conversion
        assertThat(shown(activity, temperature)).isEqualTo("294.7 K");
        temperature.setDisplayUnit("degree_fahrenheit");
        assertThat(shown(activity, temperature)).isEqualTo("70.7 °F");

        EditElement length = element(activity, 3, EditElement.class);
        length.setDisplayUnit("foot");
        assertThat(shown(activity, length)).isEqualTo("1.64042");
        assertThat(length.displayUnitSymbol()).isEqualTo("ft");
        //a typed value arrives in the buffer converted back
        type(length, "1");
        length.onMayWriteToBuffers(activity.experiment);
        assertThat(activity.experiment.getBuffer("length").value).isWithin(1e-9).of(0.3048);
        //text typed but not yet committed is dropped by a unit switch; the field shows the current value converted
        field(length).requestFocus();
        field(length).setText("7");
        length.setDisplayUnit("centi_meter");
        assertThat(shown(activity, length)).isEqualTo("30.48");
        assertThat(activity.experiment.getBuffer("length").value).isWithin(1e-9).of(0.3048);
        //the limits are checked in buffer units: 10 m typed as 1000 cm is clamped to max 2.0
        type(length, "1000");
        length.onMayWriteToBuffers(activity.experiment);
        assertThat(activity.experiment.getBuffer("length").value).isWithin(1e-9).of(2.0);
        activity.experiment.getBuffer("length").clear(false);
        activity.experiment.getBuffer("length").append(before[2]);

        GraphElement accelerationElement = element(activity, 4, GraphElement.class);
        InteractiveGraphView igv = (InteractiveGraphView) accelerationElement.rootView;
        GraphView acceleration = igv.graphView;
        acceleration.setScaleModeY(GraphView.ScaleMode.fixed, 0, GraphView.ScaleMode.fixed, 10);
        acceleration.setScaleModeX(GraphView.ScaleMode.fixed, 0, GraphView.ScaleMode.fixed, 10);
        draw(acceleration);
        assertThat(acceleration.lastYTicLabels).asList().containsExactly("0", "2", "4", "6", "8").inOrder();
        //the picker: points and their difference in s and m/s², the slope in unitYperX
        igv.showPointInfo(10, 10, 1, 2, Float.NaN, 0);
        igv.showPointInfo(20, 20, 3, 6, Float.NaN, 1);
        assertThat(readOut(igv)).isEqualTo("Difference\n    2.00000 s\n    4.00000 m/s²\nSlope\n    2.00000m/s");
        igv.hidePointInfo(1);
        assertThat(readOut(igv)).isEqualTo("Point\n    1.00000 s\n    2.00000 m/s²");

        igv.setDisplayUnit(GraphView.AXIS_Y, "foot_per_square_second");
        assertThat(accelerationElement.getDisplayUnitId(GraphView.AXIS_Y)).isEqualTo("foot_per_square_second");
        assertThat(acceleration.getLabelAndUnitY()).isEqualTo("a (ft/s²)");
        assertThat(readOut(igv)).isEqualTo("Point\n    1.00000 s\n    6.56168 ft/s²");
        draw(acceleration);
        assertThat(acceleration.lastYTicLabels).asList().containsExactly("0", "10", "20", "30").inOrder();
        igv.showPointInfo(10, 10, 1, 2, Float.NaN, 0);
        igv.showPointInfo(20, 20, 3, 6, Float.NaN, 1);
        //the slope is scaled by the ratio of the axis scales and its unit composed from the display symbols
        assertThat(readOut(igv)).isEqualTo("Difference\n    2.00000 s\n    13.1234 ft/s²\nSlope\n    6.56168 ft/s² / s");
        igv.setDisplayUnit(GraphView.AXIS_X, "milli_second");
        assertThat(acceleration.getLabelAndUnitX()).isEqualTo("t (ms)");
        assertThat(readOut(igv)).isEqualTo("Difference\n    2000.00 ms\n    13.1234 ft/s²\nSlope\n    0.00656168 ft/s² / ms");

        GraphView deprecatedPlaceholder = graphView(element(activity, 5, GraphElement.class));
        deprecatedPlaceholder.setDisplayUnit(GraphView.AXIS_X, "milli_second");
        assertThat(deprecatedPlaceholder.getLabelAndUnitX()).isEqualTo("t (ms)");

        assertThat(buffers(activity)).isEqualTo(before);
    }

    @Test
    public void theExclusionsOfferNoConversion() throws Exception {
        setting("imperial");
        Experiment activity = launch("<phyphox xmlns=\"http://phyphox.org/xml\" version=\"1.21\" locale=\"en\">"
                + "<title>t</title><category>c</category><description>d</description>"
                + "<data-containers><container size=\"1\" init=\"1.5\">v</container><container size=\"1\" init=\"3\">n</container></data-containers>"
                + "<views><view label=\"v\">"
                + "<value label=\"Direction\" unit=\"@meter\" positiveUnit=\"N\" negativeUnit=\"S\"><input>v</input></value>"
                + "<edit label=\"Count\" unit=\"@meter\" decimal=\"false\"><output>n</output></edit>"
                + "<value label=\"Angle\" unit=\"@degree\" format=\"degree-minutes\"><input>v</input></value>"
                + "<value label=\"Level\" unit=\"@decibel\"><input>v</input></value>"
                + "</view></views></phyphox>");
        ValueElement direction = element(activity, 0, ValueElement.class);
        assertThat(direction.isConvertible()).isFalse();
        assertThat(direction.getDisplayUnitId()).isEqualTo("meter");
        assertThat(shown(activity, direction)).isEqualTo("1.50N");
        EditElement count = element(activity, 1, EditElement.class);
        assertThat(count.isConvertible()).isFalse();
        assertThat(shown(activity, count)).isEqualTo("3");
        assertThat(count.displayUnitSymbol()).isEqualTo("m");
        assertThat(element(activity, 2, ValueElement.class).isConvertible()).isFalse();
        ValueElement level = element(activity, 3, ValueElement.class);
        assertThat(level.isConvertible()).isFalse();
        assertThat(shown(activity, level)).isEqualTo("1.50 dB");
    }
}
