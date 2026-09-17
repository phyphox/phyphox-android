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
//Golden images of the OpenGL graphs (a TextureView never reaches a Robolectric canvas, so these run on
//the emulator). Goldens live in app/src/androidTest/goldens keyed by device class; to re-record, run with
//"-e phyphox.goldens record" on the T1 profiles (pixel_6 1080x2400@420dpi, 10.1in WXGA 1280x800@160dpi)
//and pull files/goldens/graphs from the app's external files directory.
@RunWith(AndroidJUnit4.class)
public class GraphSnapshotTest {

    private static final String[] FIXTURES = {
            "graphs-styles.phyphox",
            "graphs-axes.phyphox",
            "graphs-special.phyphox",
    };

    //the renderer thread draws the first frame once its surface exists
    private static final long SETTLE_MILLIS = 3000;

    //Where the tablet resources take over (smallest width 600dp); goldens are per device class.
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

    //Screenshot and cut out, not View.draw: a TextureView's content is composed by the display
    //pipeline, so drawing the hierarchy yields the axes and a hole where the curve is.
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

        //taller than the screen: cut at the edge rather than skipped
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

    //Returns null on a match; records instead when asked.
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

        //The GL renderer dithers: identical graphs differ by up to 5 per channel in a few thousand pixels.
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
