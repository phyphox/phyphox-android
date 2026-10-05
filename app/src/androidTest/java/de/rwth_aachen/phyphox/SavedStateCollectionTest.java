package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.StaleObjectException;
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
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity;
import de.rwth_aachen.phyphox.ExperimentView.ExpView;
import de.rwth_aachen.phyphox.ExperimentView.ExpViewElement;
import de.rwth_aachen.phyphox.ExperimentView.ImageElement;

// phyphox-test: saved-state-collection
//The saved-state flow (docs/saved-states.md) through the real UI: an experiment with a resource and
//some data is saved to the collection as a state, listed under "Saved experiment states" with the
//given title, reopened with its data and image, renamed (meta/state.csv and nothing else), shared
//as a zip that is itself a loadable state, and deleted with everything it owns. Hermetic: what the
//test saves it removes, and leftovers of an earlier run go first.
@RunWith(AndroidJUnit4.class)
public class SavedStateCollectionTest {

    private static final String STATE_TITLE = "T1 saved state";
    private static final String RENAMED_TITLE = "T1 renamed state";
    private static final String EXPERIMENT_TITLE = "Container fixture with resource";

    @Before
    public void clearSwitchesAndLeftovers() throws Exception {
        UiDevice.getInstance(getInstrumentation())
                .executeShellCommand("setprop debug.phyphox.autoConfirm '\"\"'");
        FixtureExperiment.suppressHints();
        removeLeftovers();
    }

    @After
    public void removeWhatWasSaved() {
        FixtureExperiment.close(FixtureExperiment.activity());
        removeLeftovers();
    }

    private static Context app() {
        return getInstrumentation().getTargetContext();
    }

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    //Every state of the fixture experiment, from this run or an earlier one
    private static void removeLeftovers() {
        for (File dir : stateDirectories()) {
            File experiment = new File(dir, SavedState.EXPERIMENT_FILE);
            try {
                String content = new String(Files.readAllBytes(experiment.toPath()), StandardCharsets.UTF_8);
                if (content.contains("<title>" + EXPERIMENT_TITLE + "</title>"))
                    deleteRecursively(dir);
            } catch (IOException e) {
                throw new AssertionError("could not inspect " + dir, e);
            }
        }
        for (File zip : new File[]{new File(app().getCacheDir(), STATE_TITLE + ".zip"), new File(app().getCacheDir(), RENAMED_TITLE + ".zip")})
            //noinspection ResultOfMethodCallIgnored
            zip.delete();
    }

    private static File[] stateDirectories() {
        File[] dirs = app().getFilesDir().listFiles((dir, name) -> name.endsWith(SavedState.DIRECTORY_SUFFIX));
        return dirs == null ? new File[0] : dirs;
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children)
                deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    //A VIEW intent on the container, the way a file manager hands one over
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

    private void settle(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private UiObject2 await(String text) {
        UiObject2 object = device().wait(Until.findObject(By.text(text)), 20000);
        assertNotNull("\"" + text + "\" never showed up", object);
        return object;
    }

    //A dialog's button, as opposed to a title or a menu entry with the same text
    private UiObject2 awaitButton(String text) {
        UiObject2 object = device().wait(Until.findObject(By.clazz("android.widget.Button").text(text)), 20000);
        assertNotNull("button \"" + text + "\" never showed up", object);
        return object;
    }

    private File awaitStateDirectory(long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            for (File dir : stateDirectories())
                if (new File(dir, SavedState.STATE_CSV).isFile())
                    return dir;
            settle(200);
        }
        throw new AssertionError("no state directory appeared in the collection");
    }

    private Experiment backToCollection() {
        Experiment open = FixtureExperiment.activity();
        FixtureExperiment.close(open);
        long deadline = System.currentTimeMillis() + 10000;
        while (FixtureExperiment.activity() != null && System.currentTimeMillis() < deadline)
            settle(100);
        FixtureExperiment.bringToForeground();
        return open;
    }

    //The entry's text in the list, scrolled into view; the list rebuilds in onResume, hence the retries
    private UiObject2 listed(String title) {
        long deadline = System.currentTimeMillis() + 40000;
        while (System.currentTimeMillis() < deadline) {
            UiObject2 entry = device().wait(Until.findObject(By.text(title)), 2000);
            if (entry != null)
                return entry;
            scrollTowards(title);
        }
        throw new AssertionError("the collection does not list \"" + title + "\"");
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

    private Experiment openFromCollection(String title) {
        FixtureExperiment.suppressHints();
        Experiment previous = backToCollection();
        long deadline = System.currentTimeMillis() + 40000;
        while (System.currentTimeMillis() < deadline) {
            UiObject2 entry = listed(title);
            try {
                entry.click();
            } catch (StaleObjectException e) {
                continue;
            }
            Experiment opened = awaitOpened(previous, 8000);
            if (opened != null)
                return opened;
            FixtureExperiment.bringToForeground();
        }
        throw new AssertionError("tapping \"" + title + "\" in the collection never opened it");
    }

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

    //The entry's own menu button: a sibling of its title inside the item layout
    private void openEntryMenu(String title) {
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            try {
                UiObject2 entry = listed(title);
                UiObject2 item = entry.getParent();
                UiObject2 menu = item == null ? null : item.findObject(By.res(app().getPackageName(), "menuButton"));
                if (menu != null) {
                    menu.click();
                    return;
                }
            } catch (StaleObjectException e) {
                //the list rebuilt - look the entry up again
            }
            settle(300);
        }
        throw new AssertionError("the entry \"" + title + "\" has no menu button");
    }

    private static Map<String, byte[]> treeContents(File dir) throws IOException {
        Map<String, byte[]> contents = new TreeMap<>();
        collect(dir, "", contents);
        return contents;
    }

    private static void collect(File dir, String prefix, Map<String, byte[]> contents) throws IOException {
        File[] entries = dir.listFiles();
        if (entries == null)
            return;
        for (File entry : entries) {
            if (entry.isDirectory())
                collect(entry, prefix + entry.getName() + "/", contents);
            else
                contents.put(prefix + entry.getName(), Files.readAllBytes(entry.toPath()));
        }
    }

    private static Drawable imageDrawable(PhyphoxExperiment experiment) {
        for (ExpView view : experiment.experimentViews)
            for (ExpViewElement element : view.flatElements())
                if (element instanceof ImageElement)
                    return ((ImageElement) element).drawable;
        throw new AssertionError("the experiment has no image element");
    }

    @Test
    public void aStateSavedToTheCollectionRoundTripsRenamesSharesAndDeletes() throws Exception {
        openContainer("with-resource.zip");
        Experiment opened = FixtureExperiment.awaitLoaded();
        byte[] source = opened.experiment.source;
        assertNotNull("the loaded experiment keeps no source", source);

        //Coming from outside it offers to be kept; this test is about the state, so decline
        UiObject2 offer = device().wait(Until.findObject(By.textContains("experiment collection")), 20000);
        assertNotNull("an experiment from a container did not offer to be saved", offer);
        awaitButton("Cancel").click();

        //Some data to carry: the only buffer gets a value and the clock a start and a pause
        getInstrumentation().runOnMainSync(() -> {
            opened.experiment.experimentTimeReference.registerEvent(ExperimentTimeReference.TimeMappingEvent.START);
            opened.experiment.getBuffer("unused").append(42.0);
            opened.experiment.experimentTimeReference.registerEvent(ExperimentTimeReference.TimeMappingEvent.PAUSE);
        });

        //Menu > Save experiment state > title > To collection
        UiObject2 overflow = device().wait(Until.findObject(By.desc("More options")), 10000);
        assertNotNull("the experiment has no overflow menu", overflow);
        overflow.click();
        await(app().getString(R.string.save_state)).click();
        UiObject2 titleField = device().wait(Until.findObject(By.res(app().getPackageName(), "editTextMeasurementName")), 10000);
        assertNotNull("the save sheet has no title field", titleField);
        titleField.setText(STATE_TITLE);
        awaitButton(app().getString(R.string.save_state_save)).click();

        //The collection holds the extracted tree: the untouched experiment file, its resource, the metadata
        File stateDir = awaitStateDirectory(20000);
        long deadline = System.currentTimeMillis() + 20000;
        while (!new File(stateDir, SavedState.STATE_CSV).isFile() && System.currentTimeMillis() < deadline)
            settle(200);
        assertArrayEquals("experiment.phyphox is not the source byte for byte", source,
                Files.readAllBytes(new File(stateDir, SavedState.EXPERIMENT_FILE).toPath()));
        assertTrue("the referenced resource was not stored with the state", new File(stateDir, "res/pic.png").isFile());
        assertEquals(STATE_TITLE, SavedState.readTitle(stateDir));
        assertNull("the stored state does not validate", SavedState.validate(stateDir));
        //the sheet closes on success
        assertTrue(device().wait(Until.gone(By.res(app().getPackageName(), "editTextMeasurementName")), 10000));

        //Listed under "Saved experiment states" with the given title and reopened with its data
        Experiment reopened = openFromCollection(STATE_TITLE);
        assertEquals(STATE_TITLE, reopened.experiment.stateTitle);
        assertEquals(EXPERIMENT_TITLE, reopened.experiment.title);
        assertTrue("a collected state does not count as local", reopened.experiment.isLocal);
        assertArrayEquals(new Double[]{42.0}, reopened.experiment.getBuffer("unused").getArray());
        assertEquals(2, reopened.experiment.experimentTimeReference.getTimeMappings().size());
        assertEquals(stateDir.getAbsolutePath(), new File(reopened.experiment.stateFolder).getAbsolutePath());
        Drawable drawable = imageDrawable(reopened.experiment);
        assertTrue("the image element of the reopened state has no bitmap", drawable instanceof BitmapDrawable);
        backToCollection();
        assertNotNull("the state is not listed under its category",
                device().wait(Until.findObject(By.text(app().getString(R.string.save_state_category))), 20000));

        //Rename: meta/state.csv changes, nothing else
        Map<String, byte[]> before = treeContents(stateDir);
        openEntryMenu(STATE_TITLE);
        await(app().getString(R.string.rename)).click();
        UiObject2 nameField = device().wait(Until.findObject(By.clazz("android.widget.EditText")), 10000);
        assertNotNull("the rename dialog has no text field", nameField);
        nameField.setText(RENAMED_TITLE);
        awaitButton(app().getString(R.string.rename)).click();
        assertTrue(device().wait(Until.gone(By.clazz("android.widget.EditText")), 10000));
        listed(RENAMED_TITLE);
        assertEquals(RENAMED_TITLE, SavedState.readTitle(stateDir));
        Map<String, byte[]> after = treeContents(stateDir);
        assertEquals("rename changed the set of files", before.keySet(), after.keySet());
        for (String path : before.keySet()) {
            if (path.equals(SavedState.STATE_CSV))
                assertFalse("rename left meta/state.csv as it was", java.util.Arrays.equals(before.get(path), after.get(path)));
            else
                assertArrayEquals("rename touched " + path, before.get(path), after.get(path));
        }

        //Share: the zip handed to the chooser is itself a loadable state
        openEntryMenu(RENAMED_TITLE);
        await(app().getString(R.string.save_state_share)).click();
        File shared = new File(app().getCacheDir(), RENAMED_TITLE + ".zip");
        deadline = System.currentTimeMillis() + 20000;
        while (!shared.isFile() && System.currentTimeMillis() < deadline)
            settle(200);
        assertTrue("no zip was built for sharing", shared.isFile());
        assertNotNull("the share chooser did not open", device().wait(Until.findObject(By.textContains("hare")), 10000));
        device().pressBack();
        try (ZipFile zip = new ZipFile(shared)) {
            assertNotNull(zip.getEntry(SavedState.STATE_CSV));
            assertNotNull(zip.getEntry("res/pic.png"));
            ZipEntry experiment = zip.getEntry(SavedState.EXPERIMENT_FILE);
            assertNotNull(experiment);
            try (InputStream in = zip.getInputStream(experiment)) {
                assertArrayEquals(source, in.readAllBytes());
            }
        }
        File extracted = new File(app().getCacheDir(), "shared-state");
        deleteRecursively(extracted);
        unzip(shared, extracted);
        assertNull("the shared zip is not a valid state", SavedState.validate(extracted));
        assertEquals(RENAMED_TITLE, SavedState.readTitle(extracted));
        deleteRecursively(extracted);

        //Delete: the directory goes, resource and all
        FixtureExperiment.bringToForeground();
        openEntryMenu(RENAMED_TITLE);
        await(app().getString(R.string.delete)).click();
        awaitButton(app().getString(R.string.delete)).click();
        deadline = System.currentTimeMillis() + 20000;
        while (stateDir.exists() && System.currentTimeMillis() < deadline)
            settle(200);
        assertFalse("the state directory survived its deletion", stateDir.exists());
        assertTrue(device().wait(Until.gone(By.text(RENAMED_TITLE)), 20000));
    }

    private static void unzip(File zipFile, File dir) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File target = new File(dir, entry.getName());
                if (entry.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    target.mkdirs();
                    continue;
                }
                //noinspection ResultOfMethodCallIgnored
                target.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1)
                        out.write(buffer, 0, n);
                }
            }
        }
    }
}
