package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;

import androidx.preference.PreferenceManager;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.ExperimentView.GroupElement;
import de.rwth_aachen.phyphox.ExperimentView.ScaleElement;

// phyphox-test: view-scale-units
//What a scale with a unit reference shows under the unit conversion (phyphox-docs views/drawing.md, "Units";
//units.md), checked without rendering on corpus/generated/view-drawing.phyphox: in the experiment's unit the tics
//follow ticStep from min with as many decimals as the step needs; under the imperial setting the Celsius scale shows
//Fahrenheit values at automatically chosen tics within the same geometry (the positions of min and max unchanged,
//converted with the offset); an explicit precision follows the precision rule; a text unit is shown verbatim and
//never converted.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi")
public class ScaleUnitsTest {

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
        return launch(new String(Files.readAllBytes(new File(corpus, "generated/view-drawing.phyphox").toPath()), StandardCharsets.UTF_8));
    }

    private Experiment launch(String xml) throws Exception {
        File target = new File(RuntimeEnvironment.getApplication().getFilesDir(), "scale-units-test.phyphox");
        Files.write(target.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Const.EXPERIMENT_XML, "scale-units-test.phyphox");
        intent.putExtra(Const.EXPERIMENT_ISASSET, false);
        controller = Robolectric.buildActivity(Experiment.class, intent).setup();
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            PhyphoxExperiment experiment = controller.get().experiment;
            if (experiment != null && experiment.loaded)
                return controller.get();
            Thread.sleep(20);
        }
        throw new AssertionError("did not load: " + (controller.get().experiment == null ? "no experiment" : controller.get().experiment.message));
    }

    //The scales of the fixture: the Celsius thermometer in the vertical group, the text-unit scale in the horizontal one
    private static ScaleElement celsius(Experiment activity) {
        return (ScaleElement) ((GroupElement) activity.experiment.experimentViews.get(0).elements.get(1)).getChildren().get(1);
    }

    private static ScaleElement textUnit(Experiment activity) {
        return (ScaleElement) ((GroupElement) activity.experiment.experimentViews.get(0).elements.get(2)).getChildren().get(1);
    }

    private static List<String> majorTexts(ScaleElement scale, double width) {
        List<String> texts = new ArrayList<>();
        for (ScaleElement.Tic tic : scale.computeTics(width))
            if (tic.major)
                texts.add(tic.text);
        return texts;
    }

    private static List<Double> majorValues(ScaleElement scale, double width) {
        List<Double> values = new ArrayList<>();
        for (ScaleElement.Tic tic : scale.computeTics(width))
            if (tic.major)
                values.add(tic.value);
        return values;
    }

    private static int minorCount(ScaleElement scale, double width) {
        int n = 0;
        for (ScaleElement.Tic tic : scale.computeTics(width))
            if (!tic.major)
                n++;
        return n;
    }

    @Test
    public void experimentSettingLaysTheTicsOutFromMinByTicStepWithTheDecimalsOfTheStep() throws Exception {
        setting("experiment");
        Experiment activity = launchCorpusFixture();
        ScaleElement celsius = celsius(activity);
        assertThat(celsius.isConvertible()).isTrue();
        assertThat(celsius.isConverted()).isFalse();
        assertThat(celsius.labelText()).isEqualTo("Temperature (°C)");
        assertThat(majorTexts(celsius, 400)).containsExactly("-20", "-10", "0", "10", "20", "30", "40", "50", "60").inOrder();
        assertThat(minorCount(celsius, 400)).isEqualTo(8); //one between each pair of major tics
        List<ScaleElement.Tic> tics = celsius.computeTics(400);
        assertThat(tics.get(0).fraction).isWithin(1e-9).of(0.0);
        assertThat(tics.get(8).fraction).isWithin(1e-9).of(1.0);

        //a step of 0.25 needs two decimals; without a label the unit alone is the label
        ScaleElement text = textUnit(activity);
        assertThat(text.isConvertible()).isFalse();
        assertThat(text.labelText()).isEqualTo("m/s²");
        assertThat(majorTexts(text, 400)).containsExactly("0.00", "0.25", "0.50", "0.75", "1.00").inOrder();
    }

    @Test
    public void imperialSettingShowsFahrenheitAtAutomaticTicsInTheSameGeometry() throws Exception {
        setting("imperial");
        Experiment activity = launchCorpusFixture();
        ScaleElement celsius = celsius(activity);
        assertThat(celsius.isConverted()).isTrue();
        assertThat(celsius.getDisplayUnitId()).isEqualTo("degree_fahrenheit");
        assertThat(celsius.labelText()).isEqualTo("Temperature (°F)");
        //the range keeps its ends: -20 °C at the start, 60 °C at the end of the baseline
        assertThat(celsius.effectiveMin()).isEqualTo(-20.0);
        assertThat(celsius.effectiveMax()).isEqualTo(60.0);
        //-4 °F to 140 °F on a 360 px baseline with 16 px text: four tics at most, so a step of 50 °F; the values are
        //converted with the offset and sit at the positions of their Celsius counterparts
        List<ScaleElement.Tic> tics = celsius.computeTics(400);
        assertThat(majorTexts(celsius, 400)).containsExactly("0", "50", "100").inOrder();
        List<Double> values = majorValues(celsius, 400);
        assertThat(values.get(0)).isWithin(1e-9).of(-160.0 / 9.0); //0 °F
        assertThat(values.get(1)).isWithin(1e-9).of(10.0); //50 °F
        assertThat(values.get(2)).isWithin(1e-9).of(340.0 / 9.0); //100 °F
        for (ScaleElement.Tic tic : tics)
            assertThat(tic.fraction).isWithin(1e-9).of((tic.value + 20) / 80);
        //a text unit is shown verbatim and never converted
        ScaleElement text = textUnit(activity);
        assertThat(text.isConverted()).isFalse();
        assertThat(text.labelText()).isEqualTo("m/s²");
        assertThat(majorTexts(text, 400)).containsExactly("0.00", "0.25", "0.50", "0.75", "1.00").inOrder();
        text.setDisplayUnit("foot");
        assertThat(text.isConverted()).isFalse();
    }

    @Test
    public void metricSettingLeavesAMetricScaleAlone() throws Exception {
        setting("metric");
        Experiment activity = launchCorpusFixture();
        ScaleElement celsius = celsius(activity);
        assertThat(celsius.isConverted()).isFalse();
        assertThat(celsius.labelText()).isEqualTo("Temperature (°C)");
        assertThat(majorTexts(celsius, 400)).containsExactly("-20", "-10", "0", "10", "20", "30", "40", "50", "60").inOrder();
    }

    @Test
    public void anExplicitPrecisionFollowsThePrecisionRuleAndTheValueEveryRhythmStaysWithTheExperimentUnit() throws Exception {
        setting("experiment");
        Experiment activity = launch("<phyphox xmlns=\"http://phyphox.org/xml\" version=\"1.21\" locale=\"en\">"
                + "<title>t</title><category>c</category><description>d</description>"
                + "<data-containers><container size=\"1\">v</container></data-containers>"
                + "<views><view label=\"v\">"
                + "<scale min=\"0\" max=\"2\" unit=\"@meter\" precision=\"2\" ticStep=\"0.5\" valueEvery=\"2\" label=\"Length\" />"
                + "</view></views></phyphox>");
        ScaleElement length = (ScaleElement) activity.experiment.experimentViews.get(0).elements.get(0);
        //in the experiment's unit: 0.5 m steps with the authored two decimals, a value at every second tic
        assertThat(majorTexts(length, 400)).containsExactly("0.00", null, "1.00", null, "2.00").inOrder();
        //centimetres: the factor 100 takes the two decimals away, the tics are chosen automatically and every one is labelled
        length.setDisplayUnit("centi_meter");
        assertThat(length.labelText()).isEqualTo("Length (cm)");
        assertThat(majorTexts(length, 400)).containsExactly("0", "50", "100", "150", "200").inOrder();
        //feet: a factor of 3.28 keeps the two decimals
        length.setDisplayUnit("foot");
        assertThat(majorTexts(length, 400)).containsExactly("0.00", "2.00", "4.00", "6.00").inOrder();
        assertThat(majorValues(length, 400).get(1)).isWithin(1e-9).of(2 * 0.3048);
        //another quantity is refused
        length.setDisplayUnit("second");
        assertThat(length.getDisplayUnitId()).isEqualTo("foot");
        length.setDisplayUnit("meter");
        assertThat(majorTexts(length, 400)).containsExactly("0.00", null, "1.00", null, "2.00").inOrder();
    }
}
