package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity;
import de.rwth_aachen.phyphox.ExperimentList.model.Const;

//The phyphox://asset=<url-encoded path> deep link (documented in phyphox-docs,
//transferring-experiments.md): ExperimentListActivity.handleIntent dispatches it as the same
//internal intent the experiment list uses to open a bundled experiment. Mirrored on iOS.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AssetDeepLinkTest {

    private ExperimentListActivity listActivity;

    @Before
    public void createActivity() {
        //Attached but not created: onCreate would build the whole experiment list, which the
        //intent dispatch under test does not need.
        listActivity = Robolectric.buildActivity(ExperimentListActivity.class).get();
        if (listActivity.getBaseContext() == null)
            Shadows.shadowOf(listActivity).callAttach(new Intent());
    }

    private Intent deepLink(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        listActivity.handleIntent(intent);
        return Shadows.shadowOf(listActivity).getNextStartedActivity();
    }

    @Test
    public void opensBundledExperimentEndToEnd() throws Exception {
        Intent started = deepLink("phyphox://asset=accelerometer.phyphox");

        assertEquals(Experiment.class.getName(), started.getComponent().getClassName());
        assertEquals("accelerometer.phyphox", started.getStringExtra(Const.EXPERIMENT_XML));
        assertTrue(started.getBooleanExtra(Const.EXPERIMENT_ISASSET, false));

        //The forwarded intent must load the bundled experiment through the real loading path
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxFile.PhyphoxStream stream = PhyphoxFile.openXMLInputStream(started, activity);
        PhyphoxExperiment experiment = PhyphoxFile.loadExperiment(stream, activity);
        assertTrue("The bundled experiment failed to load: " + experiment.message, experiment.loaded);
        assertTrue("A bundled experiment is local (no save-to-collection offer)", experiment.isLocal);
    }

    @Test
    public void decodesEncodedSubdirectoryPath() throws Exception {
        Intent started = deepLink("phyphox://asset=bluetooth%2Fphyphox_m_bmp581.phyphox");

        assertEquals("bluetooth/phyphox_m_bmp581.phyphox", started.getStringExtra(Const.EXPERIMENT_XML));

        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxFile.PhyphoxStream stream = PhyphoxFile.openXMLInputStream(started, activity);
        PhyphoxExperiment experiment = PhyphoxFile.loadExperiment(stream, activity);
        assertTrue("The bundled experiment failed to load: " + experiment.message, experiment.loaded);
    }

    @Test
    public void preservesPathCase() {
        //Uri host accessors would lowercase this - the raw parse must not
        Intent started = deepLink("phyphox://asset=Foo%2FBar.phyphox");
        assertEquals("Foo/Bar.phyphox", started.getStringExtra(Const.EXPERIMENT_XML));
    }

    @Test
    public void rejectsInvalidPaths() {
        assertNull("Path traversal must not be dispatched",
                deepLink("phyphox://asset=..%2Fsecret.phyphox"));
        assertNull("Absolute paths must not be dispatched",
                deepLink("phyphox://asset=%2Fabsolute.phyphox"));
        assertNull("An empty path must not be dispatched",
                deepLink("phyphox://asset="));
    }

    @Test
    public void unknownAssetFailsWithTheNormalLoadError() {
        //Validation only guards the path shape - an unknown file dispatches and then fails in
        //the normal loading path with the app's usual could-not-load message
        Intent started = deepLink("phyphox://asset=doesnotexist.phyphox");

        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxFile.PhyphoxStream stream = PhyphoxFile.openXMLInputStream(started, activity);
        PhyphoxExperiment experiment = PhyphoxFile.loadExperiment(stream, activity);
        assertTrue("An unknown asset path must fail to load", !experiment.loaded && !experiment.message.isEmpty());
    }
}
