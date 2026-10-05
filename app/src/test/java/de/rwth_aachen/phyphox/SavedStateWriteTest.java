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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity;
import de.rwth_aachen.phyphox.ExperimentList.handler.ZipIntentHandler;

// phyphox-test: saved-state-write
//Writing the state of a running experiment (SavedState, docs/saved-states.md) and reading it back
//through the intake route: same buffers, static flags, time reference and title; the documented
//entry set and CSV dialect; experiment.phyphox byte-identical to the source; only the referenced
//resources; a legacy state's experiment file copied as it is.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SavedStateWriteTest {

    private File fixtures;
    private ExperimentListActivity listActivity;
    private Experiment experimentActivity;

    @Before
    public void createActivity() {
        fixtures = CorpusTestEnvironment.findFixtures("containers");
        assumeTrue("no phyphox-docs checkout next to this repository", fixtures != null);

        Application application = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);

        listActivity = Robolectric.buildActivity(ExperimentListActivity.class).get();
        if (listActivity.getBaseContext() == null)
            Shadows.shadowOf(listActivity).callAttach(new Intent());
        experimentActivity = CorpusTestEnvironment.fullyEquippedActivity();
    }

    private File filesDir() {
        return listActivity.getFilesDir();
    }

    private Intent viewIntent(File file) {
        return new Intent(Intent.ACTION_VIEW, Uri.fromFile(file));
    }

    //Through the zip handler, so a written container is read back the way a shared one arrives
    private PhyphoxExperiment openContainer(File archive) throws Exception {
        String result = new ZipIntentHandler(viewIntent(archive), listActivity).execute().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("the container was refused", "", result);
        Intent opened = Shadows.shadowOf(listActivity).getNextStartedActivity();
        assertNotNull("the container did not open its experiment", opened);
        return load(opened);
    }

    private PhyphoxExperiment load(Intent intent) {
        PhyphoxFile.PhyphoxStream stream = PhyphoxFile.openXMLInputStream(intent, experimentActivity);
        assertEquals("could not open " + intent.getData(), "", stream.errorMessage);
        PhyphoxExperiment experiment = PhyphoxFile.loadExperiment(stream, experimentActivity);
        assertTrue("did not load: " + experiment.message, experiment.loaded);
        return experiment;
    }

    private static byte[] read(ZipFile zip, String entry) throws Exception {
        ZipEntry e = zip.getEntry(entry);
        assertNotNull("no entry " + entry, e);
        try (InputStream in = zip.getInputStream(e)) {
            return in.readAllBytes();
        }
    }

    private static String text(ZipFile zip, String entry) throws Exception {
        return new String(read(zip, entry), StandardCharsets.UTF_8);
    }

    private static Map<String, Long> entries(ZipFile zip) {
        Map<String, Long> names = new TreeMap<>();
        Enumeration<? extends ZipEntry> all = zip.entries();
        while (all.hasMoreElements()) {
            ZipEntry e = all.nextElement();
            names.put(e.getName(), e.getSize());
        }
        return names;
    }

    //The saved-state fixture's experiment with its image and one resource nothing references
    private File sourceContainer() throws Exception {
        byte[] png;
        try (ZipFile fixture = new ZipFile(new File(fixtures, "saved-state.zip"))) {
            png = read(fixture, "res/pic.png");
        }
        File archive = new File(filesDir(), "source.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
            out.putNextEntry(new ZipEntry("saved-state.phyphox"));
            out.write(Files.readAllBytes(new File(new File(fixtures, "src"), "saved-state.phyphox").toPath()));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("res/pic.png"));
            out.write(png);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("res/unreferenced.png"));
            out.write(png);
            out.closeEntry();
        }
        return archive;
    }

    private File writeState(PhyphoxExperiment experiment, String title, String name) throws Exception {
        File archive = new File(filesDir(), name);
        try (FileOutputStream out = new FileOutputStream(archive)) {
            assertNull(experiment.writeStateFile(experimentActivity, title, out));
        }
        return archive;
    }

    @Test
    public void aWrittenStateHasTheDocumentedEntriesAndDialect() throws Exception {
        PhyphoxExperiment experiment = openContainer(sourceContainer());
        byte[] source = Files.readAllBytes(new File(new File(fixtures, "src"), "saved-state.phyphox").toPath());

        //A measurement: data in the series, the static buffer filled, the init of max x pushed out
        experiment.experimentTimeReference.registerEvent(ExperimentTimeReference.TimeMappingEvent.START);
        experiment.getBuffer("t").append(new Double[]{0.0, 0.5, 1.0, 1.5}, 4);
        experiment.getBuffer("x").append(new Double[]{1.0, Double.NaN, -2.5, Double.POSITIVE_INFINITY}, 4);
        experiment.getBuffer("calibration").append(9.81);
        experiment.getBuffer("calibration").markSet();
        experiment.getBuffer("max x (m/s²)").append(7.0);
        experiment.experimentTimeReference.registerEvent(ExperimentTimeReference.TimeMappingEvent.PAUSE);

        File archive = writeState(experiment, "Round trip, with a \"quote\"", "written.zip");

        try (ZipFile zip = new ZipFile(archive)) {
            //Exactly the documented entry set; the unreferenced resource stays behind
            assertEquals("[data/calibration.bin, data/empty.bin, data/index.csv, data/max_x__m_s__.bin, data/t.bin, data/x.bin, "
                            + "experiment.phyphox, meta/device.csv, meta/state.csv, meta/time.csv, res/pic.png]",
                    entries(zip).keySet().toString());

            assertArrayEquals("experiment.phyphox is not the source byte for byte", source, read(zip, "experiment.phyphox"));

            //The index in data-containers order, one row per container, the writer's entry names
            assertEquals("\"container\",\"file\",\"count\"\n"
                    + "\"t\",\"t.bin\",4\n"
                    + "\"x\",\"x.bin\",4\n"
                    + "\"calibration\",\"calibration.bin\",1\n"
                    + "\"empty\",\"empty.bin\",0\n"
                    + "\"max x (m/s²)\",\"max_x__m_s__.bin\",3\n", text(zip, "data/index.csv"));
            assertEquals(32, read(zip, "data/t.bin").length);
            assertEquals(0, read(zip, "data/empty.bin").length);
            assertEquals(24, read(zip, "data/max_x__m_s__.bin").length);

            //The fixed dialect regardless of the export settings: comma, dot, quoted strings, LF
            String time = text(zip, "meta/time.csv");
            String[] lines = time.split("\n");
            assertEquals("\"event\",\"experiment time\",\"system time\",\"system time text\"", lines[0]);
            assertEquals(3, lines.length);
            Pattern row = Pattern.compile("\"(START|PAUSE)\",-?\\d\\.\\d{9}E-?\\d+,\\d+\\.\\d{3},\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} UTC[+-]\\d{2}:\\d{2}\"");
            assertTrue(lines[1], row.matcher(lines[1]).matches() && lines[1].startsWith("\"START\",0.000000000E0,"));
            assertTrue(lines[2], row.matcher(lines[2]).matches() && lines[2].startsWith("\"PAUSE\","));
            assertFalse("CR in a CSV of the state", time.contains("\r"));

            String state = text(zip, "meta/state.csv");
            assertTrue(state, state.startsWith("\"property\",\"value\"\n\"format\",\"1\"\n\"title\",\"Round trip, with a \"\"quote\"\"\"\n\"saved\","));
            assertTrue(state, Pattern.compile("\n\"saved\",\"\\d+\\.\\d{3}\"\n\"saved text\",\"\\d{4}-\\d{2}-\\d{2} [^\"]*\"\n").matcher(state).find());
            assertTrue(state, state.endsWith("\"app\",\"phyphox " + BuildConfig.VERSION_NAME + " (Android)\"\n"));

            String device = text(zip, "meta/device.csv");
            assertTrue(device, device.startsWith("\"property\",\"value\"\n\"version\",\""));
            assertFalse("the export's separator setting leaked into the state", device.contains(";") || device.contains("\t"));
        }
    }

    @Test
    public void aWrittenStateReadsBackAsTheSameState() throws Exception {
        PhyphoxExperiment experiment = openContainer(sourceContainer());
        experiment.experimentTimeReference.registerEvent(ExperimentTimeReference.TimeMappingEvent.START);
        experiment.getBuffer("t").append(new Double[]{0.0, 0.5, 1.0, 1.5}, 4);
        experiment.getBuffer("x").append(new Double[]{1.0, Double.NaN, -2.5, Double.POSITIVE_INFINITY}, 4);
        experiment.getBuffer("calibration").append(9.81);
        experiment.getBuffer("calibration").markSet();
        experiment.getBuffer("max x (m/s²)").append(7.0);
        experiment.experimentTimeReference.registerEvent(ExperimentTimeReference.TimeMappingEvent.PAUSE);
        List<ExperimentTimeReference.TimeMapping> written = experiment.experimentTimeReference.getTimeMappings();

        File archive = writeState(experiment, "Round trip", "roundtrip.zip");
        PhyphoxExperiment restored = openContainer(archive);

        assertEquals("Round trip", restored.stateTitle);
        for (String name : new String[]{"t", "x", "calibration", "empty", "max x (m/s²)"}) {
            assertArrayEquals(name, experiment.getBuffer(name).getArray(), restored.getBuffer(name).getArray());
            assertEquals(name + " static", experiment.getBuffer(name).isStatic, restored.getBuffer(name).isStatic);
            assertEquals(name + " set", experiment.getBuffer(name).staticAndSet, restored.getBuffer(name).staticAndSet);
        }
        assertArrayEquals(new Double[]{2.0, 3.0, 7.0}, restored.getBuffer("max x (m/s²)").getArray());

        List<ExperimentTimeReference.TimeMapping> read = restored.experimentTimeReference.getTimeMappings();
        assertEquals(written.size(), read.size());
        for (int i = 0; i < written.size(); i++) {
            assertEquals(written.get(i).event, read.get(i).event);
            //the experiment time keeps nine significant digits, the system time its milliseconds
            assertEquals(written.get(i).experimentTime, read.get(i).experimentTime, 1e-9 * Math.max(1, Math.abs(written.get(i).experimentTime)));
            assertEquals(written.get(i).systemTime, read.get(i).systemTime);
        }

        //Saving the restored state again carries the same experiment file
        File again = writeState(restored, "Round trip", "roundtrip2.zip");
        try (ZipFile first = new ZipFile(archive); ZipFile second = new ZipFile(again)) {
            assertArrayEquals(read(first, "experiment.phyphox"), read(second, "experiment.phyphox"));
            assertArrayEquals(read(first, "data/x.bin"), read(second, "data/x.bin"));
        }
    }

    @Test
    public void aLegacyStatesExperimentFileIsCopiedAsItIs() throws Exception {
        File corpus = CorpusTestEnvironment.findCorpus();
        assumeTrue("no corpus", corpus != null);
        File legacy = new File(corpus, "generated/events-state.phyphox");
        assumeTrue("corpus fixture missing", legacy.isFile());
        byte[] legacyBytes = Files.readAllBytes(legacy.toPath());
        File copy = new File(filesDir(), "legacy.phyphox");
        Files.write(copy.toPath(), legacyBytes);

        PhyphoxExperiment experiment = load(viewIntent(copy));
        experiment.getBuffer("values").append(4.0);

        File archive = writeState(experiment, "Legacy, re-saved", "legacy-state.zip");
        try (ZipFile zip = new ZipFile(archive)) {
            //state-title, events and the init data included: nothing is stripped
            assertArrayEquals(legacyBytes, read(zip, "experiment.phyphox"));
            assertEquals("\"container\",\"file\",\"count\"\n\"values\",\"values.bin\",4\n", text(zip, "data/index.csv"));
            assertEquals(5, text(zip, "meta/time.csv").split("\n").length);
        }

        //Read back, the container wins over the file's state-title and events
        PhyphoxExperiment restored = openContainer(archive);
        assertEquals("Legacy, re-saved", restored.stateTitle);
        assertArrayEquals(new Double[]{1.0, 2.0, 3.0, 4.0}, restored.getBuffer("values").getArray());
        assertEquals(4, restored.experimentTimeReference.getTimeMappings().size());
        assertEquals(1755080067250L, restored.experimentTimeReference.getTimeMappings().get(3).systemTime);
    }

    @Test
    public void entryNamesFollowTheWriterRuleAndCollisionsAreNumbered() throws Exception {
        assertEquals("max_x__m_s__", SavedState.entryName("max x (m/s²)"));
        assertEquals("a-b_c", SavedState.entryName("a-b_c"));
        assertEquals(64, SavedState.entryName("x".repeat(100)).length());

        //Two containers that map to the same entry name
        String xml = "<phyphox version=\"1.18\"><title>Collision</title><category>Test</category>"
                + "<data-containers><container size=\"0\">a b</container><container size=\"0\">a_b</container><container size=\"0\">a?b</container></data-containers>"
                + "<views><view label=\"v\"><value label=\"a\"><input>a_b</input></value></view></views></phyphox>";
        File file = new File(filesDir(), "collision.phyphox");
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        PhyphoxExperiment experiment = load(viewIntent(file));
        experiment.getBuffer("a b").append(1.0);
        experiment.getBuffer("a_b").append(2.0);
        experiment.getBuffer("a?b").append(3.0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertNull(experiment.writeStateFile(experimentActivity, "Collision", out));
        File archive = new File(filesDir(), "collision.zip");
        Files.write(archive.toPath(), out.toByteArray());
        try (ZipFile zip = new ZipFile(archive)) {
            assertEquals("\"container\",\"file\",\"count\"\n"
                    + "\"a b\",\"a_b.bin\",1\n"
                    + "\"a_b\",\"a_b-2.bin\",1\n"
                    + "\"a?b\",\"a_b-3.bin\",1\n", text(zip, "data/index.csv"));
        }
        //and the reader goes by the index, never by the name
        PhyphoxExperiment restored = openContainer(archive);
        assertArrayEquals(new Double[]{1.0}, restored.getBuffer("a b").getArray());
        assertArrayEquals(new Double[]{2.0}, restored.getBuffer("a_b").getArray());
        assertArrayEquals(new Double[]{3.0}, restored.getBuffer("a?b").getArray());
    }
}
