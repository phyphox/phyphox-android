package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.rwth_aachen.phyphox.ExperimentList.ExperimentListActivity;
import de.rwth_aachen.phyphox.ExperimentList.handler.ZipIntentHandler;
import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.helper.Helper;

// phyphox-test: containers-load
//A .phyphox file is not necessarily bare XML: a zip archive carrying several experiments and/or
//the res/ images a view element names is a container form of the format, and so is the headerless
//"partial zip" that QR codes and Bluetooth transfers carry. All of that is contract (see the
//container forms in phyphox-docs, fixtures/containers/README.md) and had no test on either
//platform until now.
//
//The fixtures come from that same directory, so both apps pin the same bytes. What matters here
//is that they go through the REAL intake route - the signature sniffing in
//ExperimentListActivity, ZipIntentHandler's extraction with its filter and its
//path-traversal guard, zipReady's dispatch and PhyphoxFile's loading - and not through a lenient
//unzip that would prove nothing about the app.
//
//ZipIntentHandler is an AsyncTask, driven here by execute().get(): that runs the real
//doInBackground and waits for it, and idling the main looper afterwards delivers the real
//onPostExecute, which is what calls zipReady. Nothing is reimplemented.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ContainerIntakeTest {

    private File fixtures;
    private ExperimentListActivity listActivity;
    private Experiment experimentActivity;

    @Before
    public void createActivity() {
        fixtures = CorpusTestEnvironment.findFixtures("containers");
        assumeTrue("no phyphox-docs checkout next to this repository", fixtures != null);

        Application application = ApplicationProvider.getApplicationContext();
        //A container usually arrives from a file manager, i.e. from outside the app's own
        //directory, which is what openXMLInputStream asks this permission for.
        Shadows.shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);

        //Attached but not created: onCreate would build the whole experiment list, which the
        //intake under test does not need (the same shortcut AssetDeepLinkTest takes).
        listActivity = Robolectric.buildActivity(ExperimentListActivity.class).get();
        if (listActivity.getBaseContext() == null)
            Shadows.shadowOf(listActivity).callAttach(new Intent());
    }

    private File fixture(String name) {
        File file = new File(fixtures, name);
        assertTrue("missing fixture " + name + " - run phyphox-docs/tools/make_containers.py",
                file.isFile());
        return file;
    }

    private Intent viewIntent(File file) {
        return new Intent(Intent.ACTION_VIEW, Uri.fromFile(file));
    }

    //The extraction as the app performs it, plus the callback that decides what happens next.
    private String unpack(Intent intent) throws Exception {
        String result = new ZipIntentHandler(intent, listActivity).execute().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return result;
    }

    private File tempZip() {
        return new File(listActivity.getFilesDir(), "temp_zip");
    }

    //Loading an extracted experiment the way the app does: through the intent zipReady builds
    //for it, i.e. a file uri into the extraction directory.
    private PhyphoxExperiment load(Intent intent) {
        //One per test: the simulated device it sets up may only be built once.
        if (experimentActivity == null)
            experimentActivity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxFile.PhyphoxStream stream = PhyphoxFile.openXMLInputStream(intent, experimentActivity);
        assertEquals("could not open " + intent.getData() + " from the extracted container",
                "", stream.errorMessage);
        return PhyphoxFile.loadExperiment(stream, experimentActivity);
    }

    private PhyphoxExperiment loadFromTemp(String relativePath) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.fromFile(new File(tempZip(), relativePath)));
        intent.putExtra(Const.EXPERIMENT_ISTEMP, "temp_zip");
        return load(intent);
    }

    @Test
    public void theZipSignatureDecidesHowAFileIsTakenIn() throws Exception {
        //This one needs the collection as the user sees it: the zip branch puts a progress
        //dialog up before it hands over to the handler.
        ExperimentListActivity collection =
                Robolectric.buildActivity(ExperimentListActivity.class).create().get();

        //Bare XML is forwarded to the experiment activity as it stands...
        File bare = new File(collection.getFilesDir(), "bare.phyphox");
        Files.copy(new File(new File(fixtures, "src"), "container-a.phyphox").toPath(),
                bare.toPath());
        collection.handleIntent(viewIntent(bare));
        Intent forwarded = Shadows.shadowOf(collection).getNextStartedActivity();
        assertNotNull("a bare experiment file was not forwarded anywhere", forwarded);
        assertEquals(Experiment.class.getName(), forwarded.getComponent().getClassName());

        //...while the PK\x03\x04 signature routes the same kind of intent into the zip handler,
        //which unpacks first and only then decides what to open.
        collection.handleIntent(viewIntent(fixture("two-experiments.zip")));
        assertNull("a zip container was forwarded as if it were an experiment file",
                Shadows.shadowOf(collection).getNextStartedActivity());
    }

    @Test
    public void aMultiExperimentZipUnpacksToExactlyItsExperiments() throws Exception {
        //Several experiments end in the chooser rather than in an experiment, and the chooser is
        //an AppCompat dialog - the theme is what the created activity would bring along.
        listActivity.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
        assertEquals("", unpack(viewIntent(fixture("two-experiments.zip"))));

        assertNull("a container of several experiments opened one of them without asking",
                Shadows.shadowOf(listActivity).getNextStartedActivity());
        assertNotNull("no chooser was offered for a container of several experiments",
                org.robolectric.shadows.ShadowDialog.getLatestDialog());

        Collection<File> extracted = FileUtils.listFiles(tempZip(), new String[]{"phyphox"}, true);
        assertEquals("the container did not unpack to exactly its two experiments: " + extracted,
                2, extracted.size());

        //Both load, and they are the two the fixture names - this is the set the chooser offers
        //(the chooser itself, and saving from it, is the save-to-collection row).
        PhyphoxExperiment a = loadFromTemp("container-a.phyphox");
        PhyphoxExperiment b = loadFromTemp("container-b.phyphox");
        assertTrue("container-a did not load: " + a.message, a.loaded);
        assertTrue("container-b did not load: " + b.message, b.loaded);
        assertEquals("Container fixture A", a.title);
        assertEquals("Container fixture B", b.title);

        //Opened out of a container, so it is not part of the collection yet - which is what makes
        //the app offer to save it.
        assertFalse("an experiment from a container counts as local", a.isLocal);
    }

    @Test
    public void aResourceZipDeliversItsImageToTheExperiment() throws Exception {
        assertEquals("", unpack(viewIntent(fixture("with-resource.zip"))));

        //A single experiment inside is opened straight away rather than offered in a chooser.
        Intent opened = Shadows.shadowOf(listActivity).getNextStartedActivity();
        assertNotNull("the single experiment in the container was not opened", opened);
        assertEquals(Experiment.class.getName(), opened.getComponent().getClassName());
        assertEquals("temp_zip", opened.getStringExtra(Const.EXPERIMENT_ISTEMP));

        //res/ entries travel with it; everything else in an archive is dropped on purpose.
        assertTrue("the bundled resource was not extracted",
                new File(tempZip(), "res/pic.png").isFile());

        assertEquals("with-resource.phyphox", opened.getData().getLastPathSegment());

        PhyphoxExperiment experiment = load(opened);
        assertTrue("the experiment did not load: " + experiment.message, experiment.loaded);
        assertTrue("the image element did not claim its resource",
                experiment.resources.contains("pic.png"));
        assertNotNull("the experiment has no resource folder", experiment.resourceFolder);
        assertTrue("the resource is not where the experiment looks for it",
                new File(experiment.resourceFolder, "pic.png").isFile());
    }

    @Test
    public void theTraversalEntryIsRejectedAndNothingLandsOutsideTheExtractionDirectory()
            throws Exception {
        String result = unpack(viewIntent(fixture("traversal.zip")));

        //The guard is what this pins: an entry whose canonical path leaves the extraction
        //directory stops the extraction with an error instead of being written.
        assertTrue("the ../ entry was not refused, the handler said: \"" + result + "\"",
                result.contains("Security exception"));
        assertFalse("the traversal entry was written outside the extraction directory",
                new File(listActivity.getFilesDir(), "evil.phyphox").exists());
        Collection<File> strays = FileUtils.listFiles(listActivity.getFilesDir(),
                new String[]{"phyphox"}, false);
        assertTrue("container entries escaped into the app's own directory: " + strays,
                strays.isEmpty());

        //And an archive that tries it is refused as a whole - nothing out of it is opened, even
        //though the legitimate entry ahead of the traversal entry had already been extracted and
        //parses fine. (iOS has no such guard at all; see the handoff note.)
        assertNull("an experiment from a tampered container was opened anyway",
                Shadows.shadowOf(listActivity).getNextStartedActivity());
        assertTrue("the legitimate entry was not extracted before the guard hit",
                new File(tempZip(), "container-a.phyphox").isFile());
        assertTrue("the legitimate entry does not parse",
                loadFromTemp("container-a.phyphox").loaded);
    }

    @Test
    public void anEntryThatOnlySharesAPrefixWithTheExtractionDirectoryIsRejected() throws Exception {
        //No fixture for this one: it is not about the container forms but about the guard's own
        //arithmetic. The extraction directory is temp_zip, so an entry resolving to temp_zipx is
        //outside it - even though one path is a plain string prefix of the other.
        byte[] xml = Files.readAllBytes(
                new File(new File(fixtures, "src"), "container-a.phyphox").toPath());
        File archive = new File(listActivity.getFilesDir(), "sibling-prefix.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("container-a.phyphox"));
            zip.write(xml);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../" + tempZip().getName() + "x.phyphox"));
            zip.write(xml);
            zip.closeEntry();
        }

        String result = unpack(viewIntent(archive));

        assertTrue("the sibling-prefix entry was not refused, the handler said: \"" + result + "\"",
                result.contains("Security exception"));
        assertFalse("the entry was written next to the extraction directory",
                new File(listActivity.getFilesDir(), tempZip().getName() + "x.phyphox").exists());
    }

    //The shapes the guard has to get right, one archive each - a refusal stops the extraction,
    //so they cannot share one. iOS pins the same set against its destination function.
    @Test
    public void theGuardJudgesEachEntryShapeCorrectly() throws Exception {
        assertRefused("res/../../evil.png");
        assertRefused("..");
        assertRefused("res/../../../evil.phyphox");

        //A leading slash is not a traversal: it resolves inside the extraction directory like any
        //other relative name, and the entry is an ordinary bundled resource.
        assertEquals("", unpack(viewIntent(archiveWith("/res/pic.png"))));
        assertTrue("a leading slash on a res entry was treated as an escape",
                new File(tempZip(), "res/pic.png").isFile());
    }

    private void assertRefused(String entryName) throws Exception {
        String result = unpack(viewIntent(archiveWith(entryName)));
        assertTrue("\"" + entryName + "\" was not refused, the handler said: \"" + result + "\"",
                result.contains("Security exception"));
        Collection<File> strays = FileUtils.listFiles(listActivity.getFilesDir(), null, false);
        for (File stray : strays)
            assertFalse("\"" + entryName + "\" wrote " + stray + " outside the extraction "
                    + "directory", stray.getName().startsWith("evil"));
    }

    //An archive holding one legitimate experiment and one entry under test, in that order, so a
    //guard that lets the second one through has already written the first.
    private File archiveWith(String entryName) throws Exception {
        byte[] xml = Files.readAllBytes(
                new File(new File(fixtures, "src"), "container-a.phyphox").toPath());
        File archive = new File(listActivity.getFilesDir(),
                "shape" + Math.abs(entryName.hashCode()) + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("container-a.phyphox"));
            zip.write(xml);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(new byte[]{1, 2, 3, 4});
            zip.closeEntry();
        }
        return archive;
    }

    @Test
    public void aPartialZipIsRebuiltAndLoads() throws Exception {
        //The QR/BLE form: a single entry's data followed by nothing but a data descriptor - no
        //local file header, no central directory. Helper.inflatePartialZip builds the missing zip
        //structure around it, and from there it is an ordinary container.
        //
        //The payload is stored, not deflated - that is what the editor emits
        //(phyphox-blockly-editor/src/utils/offlineQrCode.ts, "For STORE method: compressed size ==
        //uncompressed size") and what both apps rebuild, writing compression method 0 into the
        //header they synthesize. Reading the fixture rather than building the form here is the
        //point: a fixture that stops being what the wire carries has to fail somewhere.
        byte[] received = Files.readAllBytes(fixture("partial.bin").toPath());
        assertEquals("the fixture does not end in a data descriptor",
                "PK\u0007\b", new String(received, received.length - 16, 4,
                        StandardCharsets.ISO_8859_1));

        byte[] rebuilt = Helper.inflatePartialZip(received);
        assertTrue("the trailing PK\\x07\\x08 was not recognised as a partial zip",
                rebuilt.length > received.length);
        assertEquals(0x50, rebuilt[0] & 0xff);
        assertEquals(0x4b, rebuilt[1] & 0xff);
        assertEquals(0x03, rebuilt[2] & 0xff);
        assertEquals(0x04, rebuilt[3] & 0xff);

        //From here the app writes the rebuilt archive out and hands it to the same zip handler
        //every other container goes through (ExperimentListActivity's QR path).
        File tempPath = new File(listActivity.getFilesDir(), "temp_qr");
        assertTrue(tempPath.isDirectory() || tempPath.mkdirs());
        File zipFile = new File(tempPath, "qr.zip");
        try (FileOutputStream out = new FileOutputStream(zipFile)) {
            out.write(rebuilt);
        }

        assertEquals("", unpack(viewIntent(zipFile)));

        Intent opened = Shadows.shadowOf(listActivity).getNextStartedActivity();
        assertNotNull("the rebuilt container did not open its experiment", opened);
        //The rebuilt entry is always called a.phyphox - the original name is not transmitted.
        assertEquals("a.phyphox", opened.getData().getLastPathSegment());

        PhyphoxExperiment experiment = load(opened);
        assertTrue("the transmitted experiment did not load: " + experiment.message,
                experiment.loaded);
        assertEquals("Container fixture A", experiment.title);
    }

    //The other half of the ruling (partial-zip-intake-scope, decided 2026-08-26): the compact
    //form is accepted from the QR scanner and the Bluetooth transfer, and from nowhere else. The
    //same bytes arriving as a file the user opens are not rebuilt - handleIntent sniffs for a
    //real zip signature, and a partial zip does not carry one - so the file is taken for the
    //experiment it is not.
    @Test
    public void aPartialZipOpenedAsAFileIsNotRebuilt() throws Exception {
        File partial = fixture("partial.bin");

        listActivity.handleIntent(viewIntent(partial));

        Intent forwarded = Shadows.shadowOf(listActivity).getNextStartedActivity();
        assertNotNull("the file was not routed anywhere at all", forwarded);
        assertEquals("a partial zip opened as a file was treated as a container",
                Experiment.class.getName(), forwarded.getComponent().getClassName());

        //And it does not sneak in as bare XML either: the payload is followed by the descriptor,
        //which is not part of the document.
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxFile.PhyphoxStream stream = PhyphoxFile.openXMLInputStream(forwarded, activity);
        PhyphoxExperiment experiment = PhyphoxFile.loadExperiment(stream, activity);
        assertFalse("a partial zip opened as a file loaded as an experiment - the compact form is "
                + "for the QR scanner and the Bluetooth transfer only", experiment.loaded);
    }
}
