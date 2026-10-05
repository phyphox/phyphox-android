package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity;
import de.rwth_aachen.phyphox.ExperimentList.datasource.ExperimentRepository;
import de.rwth_aachen.phyphox.ExperimentList.handler.ZipIntentHandler;
import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentShortInfo;
import de.rwth_aachen.phyphox.ExperimentList.ui.ExperimentsInCategory;

// phyphox-test: saved-state-load
//The saved-state container (phyphox-docs docs/saved-states.md, fixtures/containers/saved-state.zip,
//shared with iOS) through the real intake route: ExperimentListActivity's zip handler extracts the
//whole tree, zipReady opens the one experiment, PhyphoxFile parses it and SavedState restores the
//buffers, the time reference and the title. The legacy .phyphox state keeps loading, and a damaged
//container is refused.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SavedStateIntakeTest {

    private File fixtures;
    private File corpus;
    private ExperimentListActivity listActivity;
    private Experiment experimentActivity;

    @Before
    public void createActivity() {
        fixtures = CorpusTestEnvironment.findFixtures("containers");
        assumeTrue("no phyphox-docs checkout next to this repository", fixtures != null);
        corpus = CorpusTestEnvironment.findCorpus();

        Application application = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);

        listActivity = Robolectric.buildActivity(ExperimentListActivity.class).get();
        if (listActivity.getBaseContext() == null)
            Shadows.shadowOf(listActivity).callAttach(new Intent());
    }

    private File fixture(String name) {
        File file = new File(fixtures, name);
        assertTrue("missing fixture " + name + " - run phyphox-docs/tools/make_containers.py",
                file.isFile());
        return file;
    }

    private Intent viewIntent(File file) {
        return new Intent(Intent.ACTION_VIEW, Uri.fromFile(file));
    }

    private String unpack(Intent intent) throws Exception {
        String result = new ZipIntentHandler(intent, listActivity).execute().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return result;
    }

    private File tempZip() {
        return new File(listActivity.getFilesDir(), "temp_zip");
    }

    private Experiment experimentActivity() {
        if (experimentActivity == null)
            experimentActivity = CorpusTestEnvironment.fullyEquippedActivity();
        return experimentActivity;
    }

    private PhyphoxExperiment load(Intent intent) {
        PhyphoxFile.PhyphoxStream stream = PhyphoxFile.openXMLInputStream(intent, experimentActivity());
        assertEquals("could not open " + intent.getData(), "", stream.errorMessage);
        return PhyphoxFile.loadExperiment(stream, experimentActivity());
    }

    private static Double[] values(PhyphoxExperiment experiment, String buffer) {
        DataBuffer b = experiment.getBuffer(buffer);
        assertNotNull("no buffer " + buffer, b);
        return b.getArray();
    }

    @Test
    public void theFixtureStateIsRestoredNotOpenedAsThePlainExperiment() throws Exception {
        assertEquals("", unpack(viewIntent(fixture("saved-state.zip"))));

        //One experiment, opened straight away, with the whole tree extracted around it
        Intent opened = Shadows.shadowOf(listActivity).getNextStartedActivity();
        assertNotNull("the state was not opened", opened);
        assertEquals(Experiment.class.getName(), opened.getComponent().getClassName());
        assertEquals("temp_zip", opened.getStringExtra(Const.EXPERIMENT_ISTEMP));
        assertEquals("experiment.phyphox", opened.getData().getLastPathSegment());
        for (String entry : new String[]{"data/index.csv", "data/max_x__m_s__.bin", "meta/time.csv", "meta/state.csv", "res/pic.png"})
            assertTrue("the container entry " + entry + " was not extracted", new File(tempZip(), entry).isFile());

        PhyphoxExperiment experiment = load(opened);
        assertTrue("the state did not load: " + experiment.message, experiment.loaded);
        assertNotNull("the experiment does not know it is a state", experiment.stateFolder);

        //Title from meta/state.csv, the experiment's own title stays what it is
        assertEquals("Fixture state", experiment.stateTitle);
        assertEquals("Container fixture saved state", experiment.title);

        //Binary values survive, NaN and Infinity included
        assertArrayEquals(new Double[]{0.0, 0.5, 1.0, 1.5}, values(experiment, "t"));
        assertArrayEquals(new Double[]{1.0, Double.NaN, -2.5, Double.POSITIVE_INFINITY}, values(experiment, "x"));

        //The static buffer is filled and marked so, the empty one is empty although its file exists
        assertArrayEquals(new Double[]{9.81}, values(experiment, "calibration"));
        assertTrue("the static buffer is not marked as set", experiment.getBuffer("calibration").staticAndSet);
        assertEquals(0, values(experiment, "empty").length);

        //The state replaces init, and the odd name is found through the index
        assertArrayEquals(new Double[]{7.0}, values(experiment, "max x (m/s²)"));

        List<ExperimentTimeReference.TimeMapping> mappings = experiment.experimentTimeReference.getTimeMappings();
        assertEquals(2, mappings.size());
        assertEquals(ExperimentTimeReference.TimeMappingEvent.START, mappings.get(0).event);
        assertEquals(0.0, mappings.get(0).experimentTime, 0);
        assertEquals(1759650000000L, mappings.get(0).systemTime);
        assertEquals(ExperimentTimeReference.TimeMappingEvent.PAUSE, mappings.get(1).event);
        assertEquals(1.5, mappings.get(1).experimentTime, 0);
        assertEquals(1759650001500L, mappings.get(1).systemTime);

        assertTrue("the image element did not claim its resource", experiment.resources.contains("pic.png"));
        assertNotNull(experiment.resourceFolder);
        assertTrue("the resource is not where the experiment looks for it",
                new File(experiment.resourceFolder, "pic.png").isFile());
    }

    @Test
    public void theCollectionListsAStateUnderSavedStatesWithItsOwnTitle() throws Exception {
        //The collection keeps the extracted tree in a .phystate directory
        assertEquals("", unpack(viewIntent(fixture("saved-state.zip"))));
        File stateDir = new File(listActivity.getFilesDir(), "fixture.phystate");
        FileUtils.copyDirectory(tempZip(), stateDir);

        ExperimentListActivity collection = Robolectric.buildActivity(ExperimentListActivity.class).create().get();
        ExperimentRepository repository = new ExperimentRepository();
        repository.loadAndShowMainExperimentList(collection);

        String category = collection.getString(R.string.save_state_category);
        ExperimentShortInfo listed = null;
        for (ExperimentsInCategory cat : repository.categories) {
            if (!cat.name.equals(category))
                continue;
            for (ExperimentShortInfo info : cat.retrieveExperiments())
                if (info.xmlFile.equals("fixture.phystate/experiment.phyphox"))
                    listed = info;
        }
        assertNotNull("the state is not listed under \"" + category + "\"", listed);
        assertEquals("Fixture state", listed.title);
        assertEquals("Container fixture saved state", listed.description);
        //The app's own colour, not the orange of the experiment file
        assertEquals(collection.getResources().getColor(R.color.phyphox_blue_60), listed.color.intColor());

        //Opened from the list, it is local and restores like the shared file
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(Const.EXPERIMENT_XML, listed.xmlFile);
        intent.putExtra(Const.EXPERIMENT_ISASSET, false);
        PhyphoxExperiment experiment = load(intent);
        assertTrue("the collected state did not load: " + experiment.message, experiment.loaded);
        assertTrue("a collected state does not count as local", experiment.isLocal);
        assertEquals("Fixture state", experiment.stateTitle);
        assertArrayEquals(new Double[]{7.0}, values(experiment, "max x (m/s²)"));
        assertEquals(new File(stateDir, "res").getAbsolutePath(), new File(experiment.resourceFolder).getAbsolutePath());

        //Rename touches the title and nothing else; delete takes the directory with it
        byte[] indexBefore = Files.readAllBytes(new File(stateDir, "data/index.csv").toPath());
        SavedState.rename(stateDir, "Renamed state");
        assertEquals("Renamed state", SavedState.readTitle(stateDir));
        assertArrayEquals(indexBefore, Files.readAllBytes(new File(stateDir, "data/index.csv").toPath()));
        String stateCsv = new String(Files.readAllBytes(new File(stateDir, "meta/state.csv").toPath()), StandardCharsets.UTF_8);
        assertTrue(stateCsv, stateCsv.contains("\"app\",\"phyphox-docs tools/make_containers.py\""));
        ExperimentRepository.deleteExperiment(collection, listed.xmlFile);
        assertFalse("the state directory survived its deletion", stateDir.exists());
    }

    @Test
    public void aLegacyStateStillLoadsFromItsFile() throws Exception {
        assumeTrue("no corpus", corpus != null);
        File legacy = new File(corpus, "generated/events-state.phyphox");
        assumeTrue("corpus fixture missing", legacy.isFile());
        File copy = new File(listActivity.getFilesDir(), "legacy.phyphox");
        Files.copy(legacy.toPath(), copy.toPath());

        PhyphoxExperiment experiment = load(viewIntent(copy));
        assertTrue("the legacy state did not load: " + experiment.message, experiment.loaded);
        assertNull("a bare file is not a container state", experiment.stateFolder);
        assertEquals("Corpus saved state 2026-08-13", experiment.stateTitle);
        assertArrayEquals(new Double[]{1.0, 2.0, 3.0}, values(experiment, "values"));
        List<ExperimentTimeReference.TimeMapping> mappings = experiment.experimentTimeReference.getTimeMappings();
        assertEquals(4, mappings.size());
        assertEquals(1755080067250L, mappings.get(3).systemTime);
        assertEquals(12.5, mappings.get(3).experimentTime, 0);
    }

    @Test
    public void aDamagedOrUnknownContainerIsRefusedAsAWhole() throws Exception {
        //count says 3, the file holds 4 values
        assertRefused(variant("count", "\"t\",\"t.bin\",4", "\"t\",\"t.bin\",3"), "does not match its count");
        //no time reference at all
        assertRefused(variant("notime", "meta/time.csv", null), "meta/time.csv is missing");
        //a format this app does not know
        assertRefused(variant("format", "\"format\",\"1\"", "\"format\",\"2\""), "format 2");
    }

    private void assertRefused(File archive, String reason) throws Exception {
        String result = unpack(viewIntent(archive));
        assertTrue("\"" + archive.getName() + "\" was not refused for the right reason, the handler said: \"" + result + "\"",
                result.contains(reason));
        assertNull("a refused state was opened anyway", Shadows.shadowOf(listActivity).getNextStartedActivity());
    }

    //The fixture with one entry's text edited, or an entry dropped when replacement is null
    private File variant(String name, String find, String replacement) throws Exception {
        File archive = new File(listActivity.getFilesDir(), "variant-" + name + ".zip");
        try (ZipFile source = new ZipFile(fixture("saved-state.zip"));
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (replacement == null && entry.getName().equals(find))
                    continue;
                byte[] data;
                try (InputStream in = source.getInputStream(entry)) {
                    data = in.readAllBytes();
                }
                if (replacement != null && entry.getName().endsWith(".csv"))
                    data = new String(data, StandardCharsets.UTF_8).replace(find, replacement).getBytes(StandardCharsets.UTF_8);
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(data);
                out.closeEntry();
            }
        }
        return archive;
    }
}
