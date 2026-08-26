package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

//The requireFill gate holds the analysis back until a buffer has collected enough data, and the
//first run after opening OR STARTING is exempt from it (spec/analysis.yml, decided 2026-08-24).
//The second half of that is what this test is about: an input that only delivers data once the
//analysis has run - audio recording discards its first read and appends from the second one on -
//deadlocks if the gate is armed on the first started pass. The gate then waits for data, the data
//waits for a run, and the run is what the gate is holding back, so the experiment stays empty
//until the user stops and starts again (found on a device with an audio loopback, 2026-08-26).
//
//The golden vectors cannot cover this: they never start their experiments, and the exemption at
//stake is precisely the one that starting restores.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RequireFillFirstRunTest {

    //requireFill names a buffer nothing ever writes, so the gate blocks every pass it is armed
    //for. The timer appends one value per pass that actually runs, which makes the number of
    //executed passes readable off "runs".
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

    //One analysis pass through the app's own driver. newUserInput is what the app sets when
    //something has to be analysed although the clock has not moved yet; without it the periodic
    //gate would swallow the pre-start passes, which is a different gate than the one under test.
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

        //What the app does while the experiment sits on screen, unstarted: analysis passes that
        //fill the views with what the containers were initialised to.
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

    //Stopping is the other half of the rule: it exempts nothing. The passes that follow a stop
    //run with the analysis inputs already consumed, so letting them through overwrites every
    //non-append result with nothing - see StopKeepsResultsTest for that symptom.
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

    //And the same once more after a stop/start cycle, which is the path that used to work while
    //the first start did not.
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
