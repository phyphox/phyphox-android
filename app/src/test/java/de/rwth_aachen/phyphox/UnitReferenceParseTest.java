package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import de.rwth_aachen.phyphox.ExperimentView.EditElement;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.GraphElement;
import de.rwth_aachen.phyphox.ExperimentView.ValueElement;

// phyphox-test: unit-reference-parse
//The unit attributes are parsed to logical units (phyphox-docs spec/rules.yml "unit-reference", spec/units.yml,
//docs/file-format/units.md): in a 1.21 file "@meter" is the known unit meter shown with the app's symbol,
//"[[unit_short_meter]]" is the same unit in every version, and a literal "m", an "@meter" in a 1.20 file and an
//unknown "@metre" in a 1.21 file are text, shown verbatim and not convertible. The three corpus files of the rule load.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UnitReferenceParseTest {

    private static String xml(String version, String unit) {
        return "<phyphox xmlns=\"http://phyphox.org/xml\" version=\"" + version + "\" locale=\"en\">"
                + "<title>t</title><category>c</category><description>d</description>"
                + "<data-containers><container size=\"1\" init=\"1.5\">v</container><container size=\"10\">t</container></data-containers>"
                + "<views><view label=\"v\">"
                + "<value label=\"a\" unit=\"" + unit + "\"><input>v</input></value>"
                + "<edit label=\"e\" unit=\"" + unit + "\"><output>v</output></edit>"
                + "<graph label=\"g\" labelX=\"x\" unitX=\"" + unit + "\" labelY=\"y\" unitY=\"" + unit + "\" unitYperX=\"" + unit + "\"><input axis=\"x\">t</input><input axis=\"y\">t</input></graph>"
                + "</view></views></phyphox>";
    }

    private Experiment activity; //one simulated device per test: the environment cannot be equipped twice

    private Experiment activity() {
        if (activity == null)
            activity = CorpusTestEnvironment.fullyEquippedActivity();
        return activity;
    }

    private PhyphoxExperiment load(String version, String unit) {
        Experiment activity = activity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(new ByteArrayInputStream(xml(version, unit).getBytes(StandardCharsets.UTF_8)), activity);
        assertThat(experiment.message).isEmpty();
        assertThat(experiment.loaded).isTrue();
        return experiment;
    }

    private static ExpViewElement element(PhyphoxExperiment experiment, int i) {
        return experiment.experimentViews.get(0).elements.get(i);
    }

    private void assertAll(PhyphoxExperiment experiment, String id, String text) {
        Unit[] units = {
                ((ValueElement) element(experiment, 0)).getUnit(),
                ((EditElement) element(experiment, 1)).getUnit(),
                ((GraphElement) element(experiment, 2)).getUnitX(),
                ((GraphElement) element(experiment, 2)).getUnitY()};
        for (Unit unit : units) {
            assertThat(unit.id).isEqualTo(id);
            assertThat(unit.text).isEqualTo(text);
        }
    }

    @Test
    public void aReferenceInA121FileIsTheKnownUnit() {
        PhyphoxExperiment experiment = load("1.21", "@meter");
        assertAll(experiment, "meter", null);
        ValueElement value = (ValueElement) element(experiment, 0);
        assertThat(value.getUnit().symbol(activity().getResources())).isEqualTo("m");
        assertThat(value.isConvertible()).isTrue();
        assertThat(((EditElement) element(experiment, 1)).isConvertible()).isTrue();
    }

    @Test
    public void theDeprecatedPlaceholderIsTheSameUnitInEveryVersion() {
        assertAll(load("1.21", "[[unit_short_meter]]"), "meter", null);
        assertAll(load("1.20", "[[unit_short_meter]]"), "meter", null);
        assertAll(load("1.6", "[[unit_short_meter]]"), "meter", null);
    }

    @Test
    public void literalTextStaysText() {
        PhyphoxExperiment experiment = load("1.21", "m");
        assertAll(experiment, null, "m");
        assertThat(((ValueElement) element(experiment, 0)).isConvertible()).isFalse();
        assertThat(((EditElement) element(experiment, 1)).isConvertible()).isFalse();
    }

    @Test
    public void aReferenceInAnOlderFileIsText() {
        assertAll(load("1.20", "@meter"), null, "@meter");
    }

    @Test
    public void anUnknownIdIsTextInA121File() {
        assertAll(load("1.21", "@metre"), null, "@metre");
        //an unknown placeholder is text through the translation path, as before
        assertAll(load("1.21", "[[unit_short_metre]]"), null, "[[unit_short_metre]]");
    }

    @Test
    public void theCorpusFilesOfTheRuleLoadAsSpecified() throws Exception {
        File corpus = CorpusTestEnvironment.findCorpus();
        assumeTrue("No phyphox-docs checkout found next to this repository - corpus skipped.", corpus != null);
        Experiment activity = activity();

        PhyphoxExperiment references = CorpusTestEnvironment.load(new File(corpus, "generated/unit-references.phyphox"), activity);
        assertThat(references.loaded).isTrue();
        assertThat(((ValueElement) element(references, 0)).getUnit().id).isEqualTo("centi_meter");
        assertThat(((ValueElement) element(references, 2)).getUnit().text).isEqualTo("m/s³");
        assertThat(((GraphElement) element(references, 5)).getUnitX().id).isEqualTo("second"); //[[unit_short_second]]

        PhyphoxExperiment old = CorpusTestEnvironment.load(new File(corpus, "generated/unit-reference-old-version.phyphox"), activity);
        assertThat(old.loaded).isTrue();
        assertThat(((ValueElement) element(old, 0)).getUnit().text).isEqualTo("@meter");

        PhyphoxExperiment unknown = CorpusTestEnvironment.load(new File(corpus, "invalid/unit-reference-unknown.phyphox"), activity);
        assertThat(unknown.loaded).isTrue();
        assertThat(((ValueElement) element(unknown, 0)).getUnit().text).isEqualTo("@metre");
    }
}
