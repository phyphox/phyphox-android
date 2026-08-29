package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

//A file with isLink="true" is not an experiment: it points at a web page and has no views. The
//collection list opens the link when its entry is tapped, and a file arriving by URL, QR code
//or share has to end up in the same place instead of "no valid view found" (iOS does this since
//2026-08-25 - the two must behave alike).
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LinkEntryTest {

    private static final String LINK_ENTRY =
            "<phyphox version=\"1.13\" locale=\"en\" isLink=\"true\">"
                    + "<title>Other Ways to Contribute</title>"
                    + "<category>phyphox.org</category>"
                    + "<link label=\"Contribute\">https://phyphox.org/contribute</link>"
                    + "</phyphox>";

    //The same file without the attribute is a broken experiment and must stay one.
    private static final String NOT_A_LINK = LINK_ENTRY.replace(" isLink=\"true\"", "");

    private PhyphoxExperiment load(String content, Experiment activity) {
        return CorpusTestEnvironment.load(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), activity);
    }

    @Test
    public void aLinkEntryLoadsAlthoughItHasNoViews() {
        PhyphoxExperiment experiment = load(LINK_ENTRY, CorpusTestEnvironment.fullyEquippedActivity());

        assertTrue("A link entry must load: " + experiment.message, experiment.loaded);
        assertTrue(experiment.isLink);
        assertEquals("https://phyphox.org/contribute", experiment.links.get(0).url);
    }

    @Test
    public void aFileWithoutViewsIsStillRejected() {
        PhyphoxExperiment experiment = load(NOT_A_LINK, CorpusTestEnvironment.fullyEquippedActivity());

        assertFalse("Only a link entry may come without views", experiment.loaded);
        assertTrue(experiment.message, experiment.message.contains("No valid view"));
    }

    //A browser, so that the link intent resolves the way it does on a real device.
    private void installBrowser(Experiment activity, String url) {
        ResolveInfo browser = new ResolveInfo();
        browser.activityInfo = new ActivityInfo();
        browser.activityInfo.packageName = "com.example.browser";
        browser.activityInfo.name = "BrowserActivity";
        Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(
                new Intent(Intent.ACTION_VIEW, Uri.parse(url)), browser);
    }

    @Test
    public void openingALinkEntryOpensItsLinkAndLeaves() {
        Experiment activity = Robolectric.buildActivity(Experiment.class).setup().get();
        installBrowser(activity, "https://phyphox.org/contribute");
        PhyphoxExperiment experiment = load(LINK_ENTRY, activity);
        assertTrue(experiment.loaded);

        activity.onExperimentLoaded(experiment);

        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("Opening a link entry must open its link", started);
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        assertEquals(Uri.parse("https://phyphox.org/contribute"), started.getData());
        assertTrue("The experiment view must not stay behind", activity.isFinishing());
    }

    //A URL the app cannot open is not silently swallowed: the experiment list complains about
    //it, and so does this path.
    @Test
    public void aLinkEntryWithAnUnusableUrlComplains() {
        Experiment activity = Robolectric.buildActivity(Experiment.class).setup().get();
        PhyphoxExperiment experiment = load(
                LINK_ENTRY.replace("https://phyphox.org/contribute", "ftp://phyphox.org/nope"),
                activity);
        assertTrue(experiment.loaded);

        activity.onExperimentLoaded(experiment);

        assertNull("Nothing may be opened for a URL the app cannot handle",
                Shadows.shadowOf(activity).getNextStartedActivity());
        assertNotNull("The user has to be told why nothing happened",
                org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
    }

    //The shipped link entry, so the fixture above cannot drift from the real thing.
    @Test
    public void theShippedLinkEntryIsRecognized() throws Exception {
        File asset = new File("src/main/assets/experiments/support.phyphox");
        if (!asset.isFile())
            asset = new File("app/src/main/assets/experiments/support.phyphox");
        org.junit.Assume.assumeTrue("The experiment collection submodule is not checked out",
                asset.isFile());

        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
                asset, CorpusTestEnvironment.fullyEquippedActivity());
        assertTrue("support.phyphox must load as a link entry: " + experiment.message,
                experiment.loaded);
        assertTrue(experiment.isLink);
        assertFalse("A link entry has no views", experiment.experimentViews.size() > 0);
    }
}
