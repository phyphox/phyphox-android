package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getArguments;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.rwth_aachen.phyphox.SettingsActivity.SettingsFragment;

// phyphox-test: graph-snapshots
//Golden images of the OpenGL-rendered graphs, over phyphox-docs' three graph fixtures. This is
//the emulator half of the view suites (the non-graph elements are rendered by view-snapshots in
//the JVM): a graph draws through a TextureView that a Robolectric canvas never sees.
//
//A graph element is captured whole - the axes, labels and frame come from the view hierarchy,
//the curve itself from the texture, composited into one image. TextureView.getBitmap() is the
//capture path rather than PixelCopy, which reads a SurfaceView or a window; a TextureView hands
//over its content directly.
//
//Goldens ship as test assets (app/src/androidTest/goldens), so the comparison happens on the
//device, and they are keyed by device class - a run on the tablet profile compares against
//tablet goldens. Recording writes the images to the app's external files directory instead:
//
//    adb shell am instrument -w -e phyphox.goldens record \
//        -e class de.rwth_aachen.phyphox.GraphSnapshotTest \
//        de.rwth_aachen.phyphox.test/androidx.test.runner.AndroidJUnitRunner
//    adb pull /sdcard/Android/data/de.rwth_aachen.phyphox/files/goldens/graphs \
//        app/src/androidTest/goldens/graphs
//
//Recorded once per device profile: the T1 job runs the suites on a phone and on a tablet, and
//the goldens of both live side by side (light-phone.png next to light-tablet.png).
//
//A golden is pixels, so it belongs to the exact profile it was recorded on - "tablet" is not a
//size. Record on the profiles the workflow names, or the sizes will not match:
//
//    pixel_6                 1080x2400 at 420dpi
//    10.1in WXGA (Tablet)    1280x800 at 160dpi
//
//A mismatch that reports two different sizes means the recording device was not one of them.
//
//and the images are reviewed like any other change before they become the reference.
@RunWith(AndroidJUnit4.class)
public class GraphSnapshotTest {

    private static final String[] FIXTURES = {
            "graphs-styles.phyphox",
            "graphs-axes.phyphox",
            "graphs-special.phyphox",
    };

    //A graph needs a moment after the screen appears: the renderer thread draws the first frame
    //once its surface exists.
    private static final long SETTLE_MILLIS = 3000;

    //Phone or tablet, the way the layouts decide it: a smallest width of 600dp is where the
    //tablet resources take over. The goldens are per device class, so the same suite run on a
    //tablet profile compares against tablet goldens instead of failing on the phone's.
    private String deviceClass() {
        return getInstrumentation().getTargetContext().getResources().getConfiguration()
                .smallestScreenWidthDp >= 600 ? "tablet" : "phone";
    }

    private boolean recording() {
        return "record".equals(getArguments().getString("phyphox.goldens"));
    }

    @Test
    public void graphsMatchTheirGoldens() throws Exception {
        assumeTrue("No phyphox-docs checkout was present at build time - fixtures skipped.",
                FixtureExperiment.available(FIXTURES[0]));

        List<String> findings = new ArrayList<>();
        for (String fixture : FIXTURES) {
            for (String theme : new String[]{SettingsFragment.DARK_MODE_OFF,
                    SettingsFragment.DARK_MODE_ON}) {
                String configuration =
                        (SettingsFragment.DARK_MODE_ON.equals(theme) ? "dark" : "light")
                                + "-" + deviceClass();
                findings.addAll(capture(fixture, theme, configuration));
            }
        }
        if (!findings.isEmpty())
            fail(String.join("\n  ", findings));
    }

    private List<String> capture(String fixture, String themeSetting, String configuration)
            throws Exception {
        List<String> findings = new ArrayList<>();
        FixtureExperiment.applyThemeSetting(themeSetting);

        Experiment activity = FixtureExperiment.launch(fixture);
        try {
            Thread.sleep(SETTLE_MILLIS);
            String stem = fixture.replace(".phyphox", "");
            Map<String, Integer> seen = new HashMap<>();

            for (ExpView view : activity.experiment.experimentViews) {
                for (ExpView.expViewElement element : view.elements) {
                    View rootView = element.rootView;
                    if (rootView == null || findTexture(rootView) == null)
                        continue;

                    String slug = slug(element.label);
                    int occurrence = seen.merge(slug, 1, Integer::sum);
                    if (occurrence > 1)
                        slug = slug + "-" + occurrence;

                    Bitmap rendered = capture(rootView);
                    if (rendered == null) {
                        findings.add(stem + "/" + slug + " [" + configuration
                                + "]: the graph rendered nothing at all");
                        continue;
                    }
                    String finding = compare(rendered, "graphs/" + stem + "/" + slug + "/"
                            + configuration + ".png");
                    if (finding != null)
                        findings.add(stem + "/" + slug + " [" + configuration + "]: " + finding);
                }
            }
            if (findings.isEmpty() && seen.isEmpty())
                findings.add(stem + " [" + configuration + "]: holds no graph element at all");
        } finally {
            FixtureExperiment.close(activity);
        }
        return findings;
    }

    //The element as it is on screen: scroll it into view, take a screenshot of the device and
    //cut the element out of it.
    //
    //Drawing the view hierarchy into a canvas instead does not work here: a TextureView's content
    //is composed by the display pipeline, not by View.draw, so that route yields the axes and an
    //empty hole where the curve is - and compositing the texture back in by hand puts it at the
    //wrong scale as soon as the surface size differs from the view size. A screenshot has both
    //halves already assembled, exactly as the user sees them.
    private Bitmap capture(View rootView) throws Exception {
        getInstrumentation().runOnMainSync(() ->
                rootView.requestRectangleOnScreen(
                        new Rect(0, 0, rootView.getWidth(), rootView.getHeight()), true));
        Thread.sleep(500);

        final int[] location = new int[2];
        final int[] size = new int[2];
        getInstrumentation().runOnMainSync(() -> {
            rootView.getLocationOnScreen(location);
            size[0] = rootView.getWidth();
            size[1] = rootView.getHeight();
        });
        if (size[0] == 0 || size[1] == 0)
            return null;

        Bitmap screen = getInstrumentation().getUiAutomation().takeScreenshot();
        if (screen == null)
            return null;

        //An element taller than the screen is cut at the screen edge rather than skipped: the
        //golden then covers what is visible of it, which is what a reader can review anyway.
        int left = Math.max(0, Math.min(location[0], screen.getWidth() - 1));
        int top = Math.max(0, Math.min(location[1], screen.getHeight() - 1));
        int width = Math.min(size[0], screen.getWidth() - left);
        int height = Math.min(size[1], screen.getHeight() - top);
        if (width <= 0 || height <= 0)
            return null;
        return Bitmap.createBitmap(screen, left, top, width, height);
    }

    private TextureView findTexture(View view) {
        if (view instanceof TextureView)
            return (TextureView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextureView found = findTexture(group.getChildAt(i));
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    //Compares against the golden shipped as a test asset, or records it. Returns null on a match.
    private String compare(Bitmap actual, String path) throws IOException {
        if (recording()) {
            write(actual, path);
            return null;
        }

        Bitmap expected;
        try (InputStream in = getInstrumentation().getContext().getAssets().open(path)) {
            expected = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            write(actual, path);
            return "no golden recorded yet at app/src/androidTest/goldens/" + path
                    + " - record it and review the image (the capture was written to the device)";
        }
        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            write(actual, path);
            return "size " + actual.getWidth() + "x" + actual.getHeight() + " does not match the "
                    + "golden's " + expected.getWidth() + "x" + expected.getHeight();
        }

        //The GL renderer dithers: comparing two captures of the same unchanged graph turns up a
        //few thousand pixels whose channels differ by up to 5. A pixel therefore only counts as
        //different when a channel moves by more than this, and a handful of such pixels is still
        //not a finding - a line that moved or a style that changed repaints a large share of the
        //image.
        final int channelTolerance = 8;
        final double allowedShare = 0.005;

        long differing = 0;
        int maxDelta = 0;
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                int e = expected.getPixel(x, y);
                int a = actual.getPixel(x, y);
                if (e == a)
                    continue;
                int delta = Math.max(Math.abs(((e >> 16) & 0xff) - ((a >> 16) & 0xff)),
                        Math.max(Math.abs(((e >> 8) & 0xff) - ((a >> 8) & 0xff)),
                                Math.abs((e & 0xff) - (a & 0xff))));
                maxDelta = Math.max(maxDelta, delta);
                if (delta > channelTolerance)
                    differing++;
            }
        }
        double share = (double) differing / ((long) actual.getWidth() * actual.getHeight());
        if (share <= allowedShare)
            return null;

        write(actual, path);
        return String.format(Locale.US,
                "%.2f%% of the pixels differ by more than %d per channel (%d pixels, largest "
                        + "difference %d) - the capture is on the device under the same path",
                share * 100, channelTolerance, differing, maxDelta);
    }

    private void write(Bitmap bitmap, String path) throws IOException {
        File file = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),
                "goldens/" + path);
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs())
            throw new IOException("Cannot create " + file.getParentFile());
        try (OutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private String slug(String name) {
        String slug = (name == null ? "unnamed" : name).toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "unnamed" : slug;
    }

    static {
        assertTrue(FIXTURES.length == 3);
    }
}
