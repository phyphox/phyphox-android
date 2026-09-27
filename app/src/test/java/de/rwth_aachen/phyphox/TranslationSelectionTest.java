package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import de.rwth_aachen.phyphox.ExperimentList.datasource.AssetExperimentLoader;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentListEnvironment;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentLoadInfoData;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentShortInfo;

// phyphox-test: translation-block-selection
//Which translation block a file gets (phyphox-docs file-format/index.md, "Block: translations"):
//the block matching the user's locale best, the base strings otherwise. Base strings without a
//root locale are English (rated as locale "en", like on iOS), so a German block must not be
//applied on an English device; a block carrying the root's locale stands in for the base strings.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TranslationSelectionTest {

    private static String file(String rootAttributes, String translations) {
        return "<phyphox version=\"1.13\"" + rootAttributes + ">"
                + "<title>Base title</title><category>Base category</category><description>Base description</description>"
                + "<translations>" + translations + "</translations>"
                + "<data-containers><container size=\"1\">v</container></data-containers>"
                + "<input></input><analysis></analysis>"
                + "<views><view label=\"Base view\"><value label=\"Base label\"><input>v</input></value></view></views>"
                + "</phyphox>";
    }

    private static final String GERMAN_BLOCK = "<translation locale=\"de\"><title>Deutscher Titel</title>"
            + "<category>Deutsche Kategorie</category><description>Deutsche Beschreibung</description>"
            + "<string original=\"Base label\">Deutsches Label</string></translation>";

    private static final String ENGLISH_BLOCK = "<translation locale=\"en\"><title>English title</title>"
            + "<category>English category</category><description>English description</description>"
            + "<string original=\"Base label\">English label</string></translation>";

    private static PhyphoxExperiment load(String content) {
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                CorpusTestEnvironment.fullyEquippedActivity());
        assertThat(experiment.message).isEmpty();
        assertThat(experiment.loaded).isTrue();
        return experiment;
    }

    private static String label(PhyphoxExperiment experiment) {
        return experiment.experimentViews.get(0).elements.get(0).label;
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void germanBlockWithoutRootLocaleIsNotAppliedOnAnEnglishDevice() {
        PhyphoxExperiment experiment = load(file("", GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Base title");
        assertThat(experiment.category).isEqualTo("Base category");
        assertThat(label(experiment)).isEqualTo("Base label");
    }

    @Test
    @Config(qualifiers = "en-rDE")
    public void germanBlockWithoutRootLocaleIsNotAppliedOnAnEnglishDeviceInGermany() {
        PhyphoxExperiment experiment = load(file("", GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Base title");
        assertThat(label(experiment)).isEqualTo("Base label");
    }

    //A locale without a region, as phyphox's own language setting produces one.
    @Test
    @Config(qualifiers = "en")
    public void germanBlockWithoutRootLocaleIsNotAppliedOnAnEnglishDeviceWithoutRegion() {
        PhyphoxExperiment experiment = load(file("", GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Base title");
        assertThat(label(experiment)).isEqualTo("Base label");
    }

    @Test
    @Config(qualifiers = "en")
    public void germanBlockWithEnglishRootLocaleIsNotAppliedOnAnEnglishDeviceWithoutRegion() {
        PhyphoxExperiment experiment = load(file(" locale=\"en\"", GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Base title");
        assertThat(label(experiment)).isEqualTo("Base label");
    }

    @Test
    @Config(qualifiers = "fr-rFR")
    public void germanBlockWithoutRootLocaleIsNotAppliedOnAFrenchDevice() {
        PhyphoxExperiment experiment = load(file("", GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Base title");
        assertThat(label(experiment)).isEqualTo("Base label");
    }

    @Test
    @Config(qualifiers = "de-rDE")
    public void germanBlockWithoutRootLocaleIsAppliedOnAGermanDevice() {
        PhyphoxExperiment experiment = load(file("", GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Deutscher Titel");
        assertThat(experiment.category).isEqualTo("Deutsche Kategorie");
        assertThat(experiment.description).isEqualTo("Deutsche Beschreibung");
        assertThat(label(experiment)).isEqualTo("Deutsches Label");
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void germanBlockWithEnglishRootLocaleIsNotAppliedOnAnEnglishDevice() {
        PhyphoxExperiment experiment = load(file(" locale=\"en\"", GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Base title");
        assertThat(label(experiment)).isEqualTo("Base label");
    }

    @Test
    @Config(qualifiers = "de-rDE")
    public void germanBlockWithEnglishRootLocaleIsAppliedOnAGermanDevice() {
        PhyphoxExperiment experiment = load(file(" locale=\"en\"", GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Deutscher Titel");
        assertThat(label(experiment)).isEqualTo("Deutsches Label");
    }

    //Base strings without a root locale are English. An English block cannot rate strictly better than
    //them, but it carries the base's own locale and so stands in for the base strings (iOS looks the
    //root locale up among the blocks). The bundled light experiment has no base strings at all and
    //relies on this.
    @Test
    @Config(qualifiers = "fr-rFR")
    public void englishBlockWithoutRootLocaleStandsInForTheBase() {
        PhyphoxExperiment experiment = load(file("", ENGLISH_BLOCK + GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("English title");
        assertThat(label(experiment)).isEqualTo("English label");
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void englishBlockWithoutRootLocaleStandsInForTheBaseOnAnEnglishDevice() {
        PhyphoxExperiment experiment = load(file("", ENGLISH_BLOCK + GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("English title");
        assertThat(label(experiment)).isEqualTo("English label");
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void germanBlockWithGermanRootLocaleStandsInForTheBaseOnAnEnglishDevice() {
        PhyphoxExperiment experiment = load(file(" locale=\"de\"", GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Deutscher Titel");
        assertThat(label(experiment)).isEqualTo("Deutsches Label");
    }

    //A better-rated block beats the stand-in.
    @Test
    @Config(qualifiers = "en-rUS")
    public void englishBlockBeatsTheStandInWithGermanRootLocale() {
        PhyphoxExperiment experiment = load(file(" locale=\"de\"", GERMAN_BLOCK + ENGLISH_BLOCK));
        assertThat(experiment.title).isEqualTo("English title");
        assertThat(label(experiment)).isEqualTo("English label");
    }

    //The bundled light experiment: no base title, no root locale, an "en" block among others.
    @Test
    @Config(qualifiers = "en-rUS")
    public void theBundledLightExperimentGetsItsEnglishTitle() throws Exception {
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(LIGHT, CorpusTestEnvironment.fullyEquippedActivity());
        assertThat(experiment.title).isEqualTo("Light");
        assertThat(experiment.category).isEqualTo("Raw Sensors");
    }

    //The collection list has its own scanner (AssetExperimentLoader) and must pick the same block.
    private static final File LIGHT = new File("src/main/assets/experiments/light.phyphox");

    private static ExperimentShortInfo scan(java.io.InputStream input) {
        Experiment activity = org.robolectric.Robolectric.buildActivity(Experiment.class).setup().get();
        return AssetExperimentLoader.loadExperimentShortInfo(
                new ExperimentLoadInfoData(input, "test.phyphox", null, false), new ExperimentListEnvironment(activity));
    }

    private static ExperimentShortInfo scan(String content) {
        return scan(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void theListShowsTheBundledLightExperimentWithItsEnglishTitle() throws Exception {
        try (FileInputStream input = new FileInputStream(LIGHT)) {
            ExperimentShortInfo info = scan(input);
            assertThat(info.title).isEqualTo("Light");
        }
    }

    @Test
    @Config(qualifiers = "en")
    public void theListKeepsTheBaseTitleOnAnEnglishDeviceWithoutRegion() {
        assertThat(scan(file("", GERMAN_BLOCK)).title).isEqualTo("Base title");
    }

    @Test
    @Config(qualifiers = "de-rDE")
    public void theListShowsTheGermanTitleOnAGermanDevice() {
        assertThat(scan(file("", GERMAN_BLOCK)).title).isEqualTo("Deutscher Titel");
    }

    @Test
    @Config(qualifiers = "en-rUS")
    public void theListLetsABetterBlockBeatTheStandIn() {
        assertThat(scan(file(" locale=\"de\"", GERMAN_BLOCK + ENGLISH_BLOCK)).title).isEqualTo("English title");
        assertThat(scan(file(" locale=\"de\"", ENGLISH_BLOCK + GERMAN_BLOCK)).title).isEqualTo("English title");
    }

    //With a non-English root locale the English block is a real translation and applies.
    @Test
    @Config(qualifiers = "en-rUS")
    public void englishBlockWithGermanRootLocaleIsAppliedOnAnEnglishDevice() {
        PhyphoxExperiment experiment = load(file(" locale=\"de\"", ENGLISH_BLOCK));
        assertThat(experiment.title).isEqualTo("English title");
        assertThat(label(experiment)).isEqualTo("English label");
    }

    @Test
    @Config(qualifiers = "de-rDE")
    public void germanDeviceGetsTheGermanBlockAheadOfTheEnglishOne() {
        PhyphoxExperiment experiment = load(file("", ENGLISH_BLOCK + GERMAN_BLOCK));
        assertThat(experiment.title).isEqualTo("Deutscher Titel");
        assertThat(label(experiment)).isEqualTo("Deutsches Label");
    }
}
