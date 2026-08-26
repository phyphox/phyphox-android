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
//Saving an experiment that came from outside into the user's own collection - the one flow the
//automation switches deliberately decline (debug.phyphox.autoConfirm counts the offer as
//declined), so it needs the real UI to say yes. It is also where the container forms are more
//than a way of opening something: the resource an archive delivers has to survive the move into
//the collection, into the per-experiment folder named by the hex CRC32 of the experiment file,
//or the experiment reopens without its image.
//
//The archives are phyphox-docs' container fixtures (fixtures/containers/), the same ones the T0
//intake test uses, and they arrive here the way a file manager hands one over: a VIEW intent on
//a file, which the collection sniffs and unpacks.
//
//Hermetic: everything this saves is removed again afterwards, and leftovers from an interrupted
//run are cleared before it starts - the collection refuses to save an experiment it already has,
//which would make a rerun fail for the wrong reason.
@RunWith(AndroidJUnit4.class)
public class SaveToCollectionTest {

    //Both fixture experiments declare this category, which is how a leftover is recognised.
    private static final String FIXTURE_MARKER = "<category>Container fixtures</category>";

    private Set<String> before;

    @Before
    public void clearSwitchesAndLeftovers() throws Exception {
        //The offer is what this suite is about, so the switch that declines it must be off.
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

    //Everything this suite could have left behind: the saved experiment files themselves,
    //recognised by the fixture category, and the resource folders named after their CRC32.
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

    //Hands a container to the collection the way a file manager does: a VIEW intent on the file.
    //The component is set explicitly, which is also what the app itself does with file uris.
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

    //The files the collection gained, waited for: saving runs off the main thread.
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

    //The collection as the user reaches it, with the experiment closed: a save that writes the
    //file but never registers or refreshes the entry has to fail here, which is why this does not
    //look at the directory or open anything by intent.
    private UiObject2 findInCollection(String title) throws Exception {
        FixtureExperiment.close(FixtureExperiment.activity());
        FixtureExperiment.bringToForeground();

        UiObject2 entry = device().wait(Until.findObject(By.text(title)), 10000);
        if (entry == null) {
            try {
                UiScrollable list = new UiScrollable(new UiSelector().scrollable(true));
                list.setAsVerticalList();
                list.scrollTextIntoView(title);
            } catch (UiObjectNotFoundException e) {
                //Nothing to scroll, or the entry is not there at all - the assertion below says so
            }
            entry = device().wait(Until.findObject(By.text(title)), 5000);
        }
        assertNotNull("the collection does not list \"" + title + "\" after saving it", entry);
        return entry;
    }

    //Opens an entry by tapping it in the collection, the way the user does.
    private Experiment openFromCollection(String title) throws Exception {
        FixtureExperiment.suppressHints();
        findInCollection(title).click();
        return FixtureExperiment.awaitLoaded();
    }

    //The drawable an image element ended up with, or null if it has none. The fixture image is
    //not among the images bundled with phyphox, so anything here can only have come from the
    //experiment's own resource folder.
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

        //A single experiment in an archive opens straight away, and because it came from outside
        //the collection, it offers to be kept.
        Experiment opened = FixtureExperiment.awaitLoaded();
        long crc32 = opened.experiment.crc32;
        UiObject2 offer = device().wait(Until.findObject(By.textContains("experiment collection")),
                20000);
        assertNotNull("an experiment from a container did not offer to be saved", offer);

        UiObject2 save = device().findObject(By.text("Save to collection"));
        assertNotNull("the offer has no way to accept it", save);
        save.click();

        assertEquals("the collection did not gain the experiment", 1, awaitNewExperiments(1).size());

        //The image travels with it, into the folder named after the CRC32 of the experiment file.
        File resourceFolder = new File(app().getFilesDir(), Long.toHexString(crc32).toLowerCase());
        assertTrue("the bundled resource was not extracted into " + resourceFolder,
                new File(resourceFolder, "pic.png").isFile());

        //Reopened by tapping its entry in the collection, the experiment is local - no second
        //offer - and its image element has its picture.
        Experiment reopened = openFromCollection("Container fixture with resource");
        assertEquals("Container fixture with resource", reopened.experiment.title);
        assertTrue("a saved experiment still counts as external", reopened.experiment.isLocal);
        assertEquals("the saved experiment lost its resource folder", resourceFolder.getPath(),
                new File(reopened.experiment.resourceFolder).getPath());

        //The fixture image is not among the pictures bundled with phyphox, so an image element
        //that has one at all can only have taken it out of the saved resource folder - and it is
        //pixel for pixel the one the archive delivered.
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

        //Several experiments are offered in a chooser rather than opened, and the chooser can
        //take all of them into the collection at once.
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
