package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

//The timer module's offset1970 output is the Unix timestamp that experiment time zero maps to,
//so that experiment time plus offset is a real timestamp. Before the first start there is no
//recorded start yet and the experiment time is exactly zero, which makes the current time the
//only answer that keeps that promise - the app used to answer 1970 there
//(timer-offset1970-prestart, decided 2026-08-24; iOS falls back to Date() for the same reason).
//The golden vectors cannot pin a wall-clock value, hence this test.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TimerOffsetTest {

    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Timer offset test</title>"
                    + "<category>Test</category>"
                    + "<description>Minimal experiment for the timer offset test.</description>"
                    + "<data-containers>"
                    + "<container size=\"1\">t</container>"
                    + "<container size=\"1\">offset</container>"
                    + "</data-containers>"
                    + "<analysis>"
                    + "<timer><output as=\"out\">t</output><output as=\"offset1970\">offset</output></timer>"
                    + "</analysis>"
                    + "<views><view label=\"View\"><value label=\"v\"><input>t</input></value></view></views>"
                    + "</phyphox>";

    private PhyphoxExperiment load() {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
                new ByteArrayInputStream(EXPERIMENT.getBytes(StandardCharsets.UTF_8)), activity);
        assertTrue("Test experiment failed to load: " + experiment.message, experiment.loaded);
        return experiment;
    }

    //One analysis pass, as PhyphoxExperiment.processAnalysis runs it.
    private void runAnalysis(PhyphoxExperiment experiment, int cycle) {
        experiment.dataLock.lock();
        try {
            experiment.analysisTime = experiment.experimentTimeReference.getExperimentTime();
            experiment.analysisLinearTime = experiment.experimentTimeReference.getLinearTime();
            for (Analysis.AnalysisModule module : experiment.analysis)
                module.updateIfNotStatic(cycle);
        } finally {
            experiment.dataLock.unlock();
        }
    }

    @Test
    public void beforeTheFirstStartTheOffsetIsNow() {
        PhyphoxExperiment experiment = load();

        double before = System.currentTimeMillis() * 0.001;
        runAnalysis(experiment, 0);
        double after = System.currentTimeMillis() * 0.001;

        assertEquals("Experiment time is zero before the first start",
                0.0, experiment.getBuffer("t").value, 0.0);
        double offset = experiment.getBuffer("offset").value;
        assertTrue("offset1970 must be the current timestamp before the first start, was " + offset,
                offset >= before && offset <= after);
    }

    @Test
    public void afterTheFirstStartTheOffsetIsTheStart() {
        PhyphoxExperiment experiment = load();
        experiment.experimentTimeReference.registerEvent(ExperimentTimeReference.TimeMappingEvent.START);

        runAnalysis(experiment, 0);

        assertEquals("offset1970 is the recorded start once there is one",
                experiment.experimentTimeReference.getSystemTimeReferenceByIndex(0) * 0.001,
                experiment.getBuffer("offset").value, 0.0);
    }
}
