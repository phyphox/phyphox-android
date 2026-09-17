package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

//Stopping must not destroy what was recorded: the analysis consumes its inputs, the requireFill
//gate holds the next pass back, and the app keeps analysing while stopped (handleInputViews). A
//stop that disarmed the gate would let a paused pass overwrite every non-append output with nothing.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class StopKeepsResultsTest {

    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Stop keeps results</title>"
                    + "<category>Test</category>"
                    + "<description>A requireFill buffer the analysis consumes, as audio_scope does.</description>"
                    + "<data-containers>"
                    + "<container size=\"8\">recording</container>"
                    + "<container size=\"8\">peak</container>"
                    + "</data-containers>"
                    + "<analysis requireFill=\"recording\" requireFillThreshold=\"4\">"
                    + "<max><input as=\"y\">recording</input><output as=\"max\">peak</output></max>"
                    + "</analysis>"
                    + "<views><view label=\"View\"><value label=\"peak\"><input>peak</input></value></view></views>"
                    + "<export><set name=\"Data\"><data name=\"peak\">peak</data></set></export>"
                    + "</phyphox>";

    private Experiment activity;

    private PhyphoxExperiment load() {
        activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
                new ByteArrayInputStream(EXPERIMENT.getBytes(StandardCharsets.UTF_8)), activity);
        assertTrue("Test experiment failed to load: " + experiment.message, experiment.loaded);
        return experiment;
    }

    //the input fills the buffer, one pass turns it into a result and consumes it, then the user stops
    private PhyphoxExperiment recordAndStop() throws Exception {
        PhyphoxExperiment experiment = load();

        experiment.startAllIO();
        DataBuffer recording = experiment.getBuffer("recording");
        experiment.dataLock.lock();
        try {
            for (double v : new double[]{1, 4, 2, 3})
                recording.append(v);
        } finally {
            experiment.dataLock.unlock();
        }

        experiment.newUserInput = true;
        experiment.processAnalysis(true);
        assertEquals("the analysis did not produce its result", 4.0,
                experiment.getBuffer("peak").value, 0.0);
        assertEquals("the analysis is expected to consume its input, as audio_scope's does",
                0, recording.getFilledSize());

        experiment.stopAllIO();
        return experiment;
    }

    //while stopped the main loop calls handleInputViews, which analyses whenever there is user input
    private void pausedPass(PhyphoxExperiment experiment) {
        experiment.newUserInput = true;
        experiment.handleInputViews(false);
    }

    @Test
    public void aPausedPassAfterStoppingDoesNotWipeTheResults() throws Exception {
        PhyphoxExperiment experiment = recordAndStop();

        pausedPass(experiment);
        pausedPass(experiment);

        assertEquals("a paused pass after the stop wiped the recorded result", 1,
                experiment.getBuffer("peak").getFilledSize());
        assertEquals(4.0, experiment.getBuffer("peak").value, 0.0);
    }

    @Test
    public void theExportAfterStoppingStillHasRows() throws Exception {
        PhyphoxExperiment experiment = recordAndStop();

        pausedPass(experiment);

        File file = experiment.exporter.exportDirect(experiment.exporter.exportFormats[1],
                activity.getCacheDir(), false, "stopkeepsresults", activity);
        List<String> rows = csvRows(file, "Data");
        assertEquals("the export after a stop held only its header", 1, rows.size());
        assertTrue("the exported value is not the recorded one: " + rows.get(0),
                rows.get(0).startsWith("4"));
    }

    //The rows of one .csv inside the exported zip, without the header.
    private List<String> csvRows(File zip, String set) throws Exception {
        try (ZipFile archive = new ZipFile(zip)) {
            InputStream in = archive.getInputStream(archive.getEntry(set + ".csv"));
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1)
                out.write(buffer, 0, n);
            in.close();
            List<String> rows = new ArrayList<>();
            String[] lines = new String(out.toByteArray(), StandardCharsets.UTF_8).split("\n");
            for (int i = 1; i < lines.length; i++)
                if (!lines[i].trim().isEmpty())
                    rows.add(lines[i].trim());
            return rows;
        }
    }
}
