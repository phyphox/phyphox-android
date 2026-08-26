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

//Stopping an experiment must not destroy what it recorded.
//
//It used to. An analysis module consumes its inputs - an <input> without clear="false" empties
//the buffer it read - so once a pass has run, the recording is gone and only the results remain.
//The requireFill gate is what holds the next pass back until the recording has filled up again.
//stopAllIO used to disarm that gate, and the app keeps analysing while an experiment sits
//stopped (handleInputViews runs a pass whenever there is user input and no measurement), so the
//first such pass ran ungated on an empty recording and overwrote every non-append output with
//nothing. The values vanished from the screen and the export that followed held only headers.
//
//Found on a lab Pixel 3 (2026-08-26) as audio_scope exporting header-only files in all six
//formats. This is that experiment's shape, reduced to what matters: a buffer under requireFill
//that the analysis consumes, and a result computed from it.
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

    //A measurement: the input fills the buffer, one analysis pass turns it into a result and
    //consumes it on the way, and then the user stops.
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

    //The paused pass through the app's own entry point: while an experiment is stopped, the main
    //loop calls handleInputViews, which analyses whenever there is user input to act on.
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
