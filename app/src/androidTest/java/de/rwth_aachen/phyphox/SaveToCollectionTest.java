package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity;
import de.rwth_aachen.phyphox.helper.Helper;

// phyphox-test: save-to-collection
//Saving an experiment that came from outside into the collection - the one flow autoConfirm
//declines, so the real UI has to say yes - including the resource an archive delivers, which
//has to reach the per-experiment folder named by the hex CRC32 of the experiment file.
@RunWith(AndroidJUnit4.class)
public class SaveToCollectionTest {

    //how a leftover of this suite is recognised
    private static final String FIXTURE_MARKER = "<category>Container fixtures</category>";

    private Set<String> before;

    @Before
    public void clearSwitchesAndLeftovers() throws Exception {
        UiDevice.getInstance(getInstrumentation())
                .executeShellCommand("setprop debug.phyphox.autoConfirm '\"\"'");
        FixtureExperiment.suppressHints();
        removeSavedFixtures();
        before = fileNames();
    }

    @After
    public void removeWhatWasSaved() {
        FixtureExperiment.close(FixtureExperiment.activity());
        removeSavedFixtures();
    }

    private static Context app() {
        return getInstrumentation().getTargetContext();
    }

    private static Set<String> fileNames() {
        String[] names = app().getFilesDir().list();
        return new HashSet<>(Arrays.asList(names == null ? new String[0] : names));
    }

    //Leftovers of an earlier run: the collection refuses to save an experiment it already has.
    private void removeSavedFixtures() {
        File filesDir = app().getFilesDir();
        File[] entries = filesDir.listFiles();
        if (entries == null)
            return;
        for (File entry : entries) {
            if (!entry.isFile() || !entry.getName().endsWith(".phyphox"))
                continue;
            try {
                String content = new String(Files.readAllBytes(entry.toPath()),
                        StandardCharsets.UTF_8);
                if (!content.contains(FIXTURE_MARKER))
                    continue;
                File resourceFolder = new File(filesDir,
                        Long.toHexString(Helper.getCRC32(entry)).toLowerCase());
                deleteRecursively(resourceFolder);
                //noinspection ResultOfMethodCallIgnored
                entry.delete();
            } catch (IOException e) {
                throw new AssertionError("could not inspect " + entry, e);
            }
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children)
                deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    //A VIEW intent on the file, the way a file manager hands one over.
    private void openContainer(String fixture) throws IOException {
        assumeTrue("no phyphox-docs checkout was present at build time",
                FixtureExperiment.available(fixture));
        File copy = new File(app().getFilesDir(), fixture);
        try (InputStream in = getInstrumentation().getContext().getAssets().open(fixture);
             OutputStream out = new FileOutputStream(copy)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1)
                out.write(buffer, 0, n);
        }

        Intent intent = new Intent(app(), ExperimentListActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.fromFile(copy));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        app().startActivity(intent);
    }

    private List<File> awaitNewExperiments(int expected) {
        long deadline = System.currentTimeMillis() + 20000;
        List<File> added = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            added.clear();
            for (String name : new TreeSet<>(fileNames())) {
                if (name.endsWith(".phyphox") && !before.contains(name))
                    added.add(new File(app().getFilesDir(), name));
            }
            if (added.size() >= expected)
                return added;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("the collection gained " + added.size() + " experiments, expected "
                + expected + ": " + added);
    }

    //Waits until the experiment is really gone, or the next lookup finds the one still on screen.
    private Experiment backToCollection() {
        Experiment open = FixtureExperiment.activity();
        FixtureExperiment.close(open);
        long deadline = System.currentTimeMillis() + 10000;
        while (FixtureExperiment.activity() != null && System.currentTimeMillis() < deadline)
            settle(100);
        FixtureExperiment.bringToForeground();
        return open;
    }

    //Through the collection's list, so a save that never registers its entry fails here. Retried:
    //the list rebuilds in onResume, so an entry can go stale under the tap and a tap can be swallowed.
    private Experiment openFromCollection(String title) {
        FixtureExperiment.suppressHints();
        Experiment previous = backToCollection();

        boolean everListed = false;
        long deadline = System.currentTimeMillis() + 40000;
        while (System.currentTimeMillis() < deadline) {
            UiObject2 entry = device().wait(Until.findObject(By.text(title)), 2000);
            if (entry == null) {
                scrollTowards(title);
                continue;
            }
            everListed = true;
            try {
                entry.click();
            } catch (StaleObjectException e) {
                continue; //the list rebuilt under the tap - look it up again
            }
            Experiment opened = awaitOpened(previous, 8000);
            if (opened != null)
                return opened;
            FixtureExperiment.bringToForeground(); //the tap did not take, try once more
        }
        throw new AssertionError(everListed
                ? "tapping \"" + title + "\" in the collection never opened it"
                : "the collection does not list \"" + title + "\" after saving it");
    }

    //By identity, not title: a reopened experiment has the same title as the one just closed.
    private Experiment awaitOpened(Experiment previous, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            Experiment activity = FixtureExperiment.activity();
            if (activity != null && activity != previous
                    && activity.experiment != null && activity.experiment.loaded)
                return activity;
            settle(100);
        }
        return null;
    }

    private void scrollTowards(String title) {
        try {
            UiScrollable list = new UiScrollable(new UiSelector().scrollable(true));
            list.setAsVerticalList();
            list.scrollTextIntoView(title);
        } catch (UiObjectNotFoundException e) {
            //nothing to scroll, or not there yet - the caller's deadline decides
        }
    }

    private void settle(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    //The fixture image is not bundled with phyphox, so any drawable came from the resource folder.
    private static Drawable imageDrawable(PhyphoxExperiment experiment) {
        for (ExpView view : experiment.experimentViews)
            for (ExpView.expViewElement element : view.elements)
                if (element instanceof ExpView.imageElement)
                    return ((ExpView.imageElement) element).drawable;
        throw new AssertionError("the experiment has no image element");
    }

    @Test
    public void aBundledResourceSurvivesTheMoveIntoTheCollection() throws Exception {
        openContainer("with-resource.zip");

        //A single experiment opens straight away and, coming from outside, offers to be kept.
        Experiment opened = FixtureExperiment.awaitLoaded();
        long crc32 = opened.experiment.crc32;
        UiObject2 offer = device().wait(Until.findObject(By.textContains("experiment collection")),
                20000);
        assertNotNull("an experiment from a container did not offer to be saved", offer);

        UiObject2 save = device().findObject(By.text("Save to collection"));
        assertNotNull("the offer has no way to accept it", save);
        save.click();

        assertEquals("the collection did not gain the experiment", 1, awaitNewExperiments(1).size());

        File resourceFolder = new File(app().getFilesDir(), Long.toHexString(crc32).toLowerCase());
        assertTrue("the bundled resource was not extracted into " + resourceFolder,
                new File(resourceFolder, "pic.png").isFile());

        Experiment reopened = openFromCollection("Container fixture with resource");
        assertEquals("Container fixture with resource", reopened.experiment.title);
        assertTrue("a saved experiment still counts as external", reopened.experiment.isLocal);
        assertEquals("the saved experiment lost its resource folder", resourceFolder.getPath(),
                new File(reopened.experiment.resourceFolder).getPath());

        Drawable drawable = imageDrawable(reopened.experiment);
        assertNotNull("the image element of the reopened experiment has no image", drawable);
        assertTrue("the image element did not end up with a bitmap",
                drawable instanceof BitmapDrawable);
        Bitmap delivered = BitmapFactory.decodeFile(
                new File(resourceFolder, "pic.png").getAbsolutePath());
        assertTrue("the image is not the one the container delivered",
                delivered.sameAs(((BitmapDrawable) drawable).getBitmap()));
    }

    @Test
    public void bothExperimentsOfAContainerCanBeSavedAtOnce() throws Exception {
        openContainer("two-experiments.zip");

        //Several experiments go to a chooser, which can save all of them at once.
        UiObject2 chooser = device().wait(Until.findObject(By.text("Save all")), 20000);
        assertNotNull("a container of several experiments offered no way to save them", chooser);
        chooser.click();

        assertEquals("the collection did not gain both experiments", 2,
                awaitNewExperiments(2).size());

        for (String title : new String[]{"Container fixture A", "Container fixture B"}) {
            Experiment reopened = openFromCollection(title);
            assertEquals(title, reopened.experiment.title);
            assertTrue("a saved experiment still counts as external", reopened.experiment.isLocal);
        }
    }
}
