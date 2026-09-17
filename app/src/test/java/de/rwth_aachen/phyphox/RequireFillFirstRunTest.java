package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

//The requireFill gate holds the analysis back until a buffer has enough data; the first pass
//after opening OR starting is exempt (phyphox-docs spec/analysis.yml). Without the starting
//exemption an input that only delivers data once the analysis has run (audio recording) deadlocks.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RequireFillFirstRunTest {

    //requireFill names a buffer nothing writes; the timer appends one value per pass that runs
    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Require fill test</title>"
                    + "<category>Test</category>"
                    + "<description>Minimal experiment for the requireFill first-run exemption.</description>"
                    + "<data-containers>"
                    + "<container size=\"10\">source</container>"
                    + "<container size=\"10\">runs</container>"
                    + "</data-containers>"
                    + "<analysis requireFill=\"source\" requireFillThreshold=\"5\">"
                    + "<timer><output append=\"true\">runs</output></timer>"
                    + "</analysis>"
                    + "<views><view label=\"View\"><value label=\"v\"><input>runs</input></value></view></views>"
                    + "</phyphox>";

    private PhyphoxExperiment load() {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
                new ByteArrayInputStream(EXPERIMENT.getBytes(StandardCharsets.UTF_8)), activity);
        assertTrue("Test experiment failed to load: " + experiment.message, experiment.loaded);
        return experiment;
    }

    //newUserInput bypasses the periodic gate, which is not the one under test
    private void runAnalysisPass(PhyphoxExperiment experiment, boolean measuring) {
        experiment.newUserInput = true;
        experiment.processAnalysis(measuring);
    }

    private int executedPasses(PhyphoxExperiment experiment) {
        return experiment.getBuffer("runs").getFilledSize();
    }

    @Test
    public void theFirstPassAfterOpeningIsExempt() {
        PhyphoxExperiment experiment = load();

        runAnalysisPass(experiment, false);
        assertEquals("the first pass after opening must run although the buffer is empty",
                1, executedPasses(experiment));

        runAnalysisPass(experiment, false);
        runAnalysisPass(experiment, false);
        assertEquals("the gate must be armed from the second pass on",
                1, executedPasses(experiment));
    }

    @Test
    public void theFirstPassAfterStartingIsExemptAgain() throws Exception {
        PhyphoxExperiment experiment = load();

        //the unstarted passes the app runs while the experiment sits on screen
        runAnalysisPass(experiment, false);
        runAnalysisPass(experiment, false);
        assertEquals(1, executedPasses(experiment));

        experiment.startAllIO();

        runAnalysisPass(experiment, true);
        assertEquals("the first pass after starting must run although the buffer is empty - an "
                        + "input that only fills once the analysis has run would deadlock",
                2, executedPasses(experiment));

        runAnalysisPass(experiment, true);
        runAnalysisPass(experiment, true);
        assertEquals("the gate must be armed again from the second started pass on",
                2, executedPasses(experiment));
    }

    //stopping exempts nothing: a pass after a stop runs on already consumed inputs and would wipe the results
    @Test
    public void stoppingDoesNotExemptAPass() throws Exception {
        PhyphoxExperiment experiment = load();

        runAnalysisPass(experiment, false);
        experiment.startAllIO();
        runAnalysisPass(experiment, true);
        assertEquals(2, executedPasses(experiment));

        experiment.stopAllIO();

        runAnalysisPass(experiment, false);
        runAnalysisPass(experiment, false);
        assertEquals("stopping must not exempt anything - the exemption is for opening and "
                        + "starting, and a run after stopping is neither",
                2, executedPasses(experiment));
    }

    @Test
    public void restartingExemptsAPassAgain() throws Exception {
        PhyphoxExperiment experiment = load();

        runAnalysisPass(experiment, false);
        experiment.startAllIO();
        runAnalysisPass(experiment, true);
        assertEquals(2, executedPasses(experiment));

        experiment.stopAllIO();
        experiment.startAllIO();

        runAnalysisPass(experiment, true);
        assertEquals("the first pass after a restart must run as well",
                3, executedPasses(experiment));
    }
}
