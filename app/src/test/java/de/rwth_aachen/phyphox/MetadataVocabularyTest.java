package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

//Every identifier from Metadata.sensorsWithMetadata() must be one Metadata accepts: the remote
//interface, the CSV export and the xlsx export all walk that list, and an identifier outside the
//vocabulary throws in the middle of their work.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MetadataVocabularyTest {

    @Test
    public void everySensorIdentifierIsAccepted() {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        for (SensorInput.SensorName sensor : Metadata.sensorsWithMetadata())
            for (Metadata.SensorMetadata sensorMetadata : Metadata.SensorMetadata.values()) {
                String identifier = sensor.name() + sensorMetadata.toString();
                try {
                    new Metadata(identifier, activity);
                } catch (IllegalArgumentException e) {
                    fail("Metadata rejects \"" + identifier + "\", which its own sensor list offers");
                }
            }
    }

    @Test
    public void theCustomSensorIsNotPartOfTheVocabulary() {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        assertFalse("Custom sensors are selected by nameFilter, so their metadata is ambiguous",
                Metadata.sensorsWithMetadata().contains(SensorInput.SensorName.custom));
        boolean rejected = false;
        try {
            new Metadata(SensorInput.SensorName.custom.name() + Metadata.SensorMetadata.Name, activity);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        assertTrue("An identifier outside the vocabulary must be rejected, not answered", rejected);
    }
}
