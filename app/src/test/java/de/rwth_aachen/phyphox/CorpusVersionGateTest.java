package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

// phyphox-test: corpus-version-gate
//A file declaring a format version newer than PhyphoxFile.phyphoxFileVersion (supported + 0.1)
//must be refused, and the identical file declaring the exact supported version must load. This
//guards the version gate that all format feature rollout relies on. The experiment is built
//from a minimal valid skeleton at test time, not a corpus fixture, so this test does not need
//the phyphox-docs checkout. Contract: phyphox-docs/corpus/README.md, "The app test suites".
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CorpusVersionGateTest {

    private static String minimalExperiment(String version) {
        return "<phyphox version=\"" + version + "\">"
                + "<title>Version gate</title>"
                + "<category>Test</category>"
                + "<description>Minimal experiment for the version gate test.</description>"
                + "<views><view label=\"View\"><info label=\"Hello\"/></view></views>"
                + "</phyphox>";
    }

    private PhyphoxExperiment load(String xml) {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        return CorpusTestEnvironment.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), activity);
    }

    @Test
    public void newerVersionIsRefused() {
        int[] supported = CorpusTestEnvironment.supportedVersion();
        String tooNew = supported[0] + "." + (supported[1] + 1);
        PhyphoxExperiment experiment = load(minimalExperiment(tooNew));
        assertFalse("An experiment declaring version " + tooNew + " (supported: "
                + PhyphoxFile.phyphoxFileVersion + ") must be refused", experiment.loaded);
    }

    @Test
    public void exactSupportedVersionLoads() {
        PhyphoxExperiment experiment = load(minimalExperiment(PhyphoxFile.phyphoxFileVersion));
        assertTrue("The minimal experiment at the supported version " + PhyphoxFile.phyphoxFileVersion
                + " failed to load: " + experiment.message, experiment.loaded);
    }
}
