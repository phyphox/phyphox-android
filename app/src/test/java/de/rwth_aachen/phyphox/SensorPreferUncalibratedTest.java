package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

// phyphox-test: sensor-prefer-uncalibrated
//The preferUncalibrated attribute (format 1.21) decides which version of a sensor the experiment
//starts with: the corpus fixture asks for the uncalibrated magnetometer and the default gyroscope.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SensorPreferUncalibratedTest {

    @Test
    public void attributeSetsTheStartingVersion() throws Exception {
        File corpus = CorpusTestEnvironment.findCorpus();
        assumeTrue("No phyphox-docs checkout found next to this repository - fixture skipped.", corpus != null);
        File file = new File(corpus, "generated/sensor-prefer-uncalibrated.phyphox");
        assumeTrue("Fixture missing in the phyphox-docs checkout - skipped.", file.isFile());

        PhyphoxExperiment experiment = CorpusTestEnvironment.load(file, CorpusTestEnvironment.fullyEquippedActivity());
        assertTrue("fixture failed to load: " + experiment.message, experiment.loaded);
        assertEquals(2, experiment.inputSensors.size());
        assertFalse("magnetometer with preferUncalibrated=\"true\" must start uncalibrated", experiment.inputSensors.get(0).calibrated);
        assertTrue("gyroscope with preferUncalibrated=\"false\" must start calibrated", experiment.inputSensors.get(1).calibrated);
    }
}
