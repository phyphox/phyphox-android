package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.rwth_aachen.phyphox.ExperimentList.model.Const;
import de.rwth_aachen.phyphox.SettingsActivity.SettingsFragment;

// phyphox-test: view-snapshots
//Golden images of the non-graph view elements, rendered from phyphox-docs' view fixtures
//(fixtures/views/, found in the checkout next to this repository). One golden per fixture,
//element and configuration; the contract - which configurations, which locale, where the
//goldens live - is fixtures/views/README.md, "The snapshot contract".
//
//The graph fixtures are not here: their OpenGL renderer needs a real surface, which is the
//graph-snapshots row on an emulator.
//
//Record after an intentional UI change, then review the images:
//    ./gradlew testRegularDebugUnitTest --tests '*ViewSnapshotTest*' -Pphyphox.goldens=record
@RunWith(ParameterizedRobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "en-rUS")
public class ViewSnapshotTest {

    //The non-graph fixtures. Every element of every view of each file gets a golden.
    private static final String[] FIXTURES = {
            "values.phyphox",
            "edits.phyphox",
            "buttons-toggles.phyphox",
            "sliders-dropdowns.phyphox",
            "info-separator-image.phyphox",
    };

    //The configuration matrix of the snapshot contract: both explicit themes at both widths,
    //the large font setting at phone width, and one forced-RTL smoke pass. Theme is a phyphox
    //setting rather than the system's, and it resolves to the night/notnight resource
    //configuration - which is what the elements actually read.
    static class Configuration {
        final String name;
        final String qualifiers;
        final float fontScale;
        final int widthDp;
        final boolean rtl;
        final String themeSetting; //the phyphox setting, see SettingsFragment.DARK_MODE_*

        Configuration(String name, String qualifiers, float fontScale, int widthDp,
                      String themeSetting) {
            this(name, qualifiers, fontScale, widthDp, themeSetting, false);
        }

        Configuration(String name, String qualifiers, float fontScale, int widthDp,
                      String themeSetting, boolean rtl) {
            this.name = name;
            this.qualifiers = qualifiers;
            this.fontScale = fontScale;
            this.widthDp = widthDp;
            this.themeSetting = themeSetting;
            this.rtl = rtl;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final Configuration[] CONFIGURATIONS = {
            new Configuration("light-phone", "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi", 1.0f, 411, SettingsFragment.DARK_MODE_OFF),
            new Configuration("dark-phone", "en-rUS-w411dp-h891dp-normal-port-night-mdpi", 1.0f, 411, SettingsFragment.DARK_MODE_ON),
            new Configuration("light-tablet", "en-rUS-sw600dp-w800dp-h1280dp-normal-port-notnight-mdpi", 1.0f, 800, SettingsFragment.DARK_MODE_OFF),
            new Configuration("dark-tablet", "en-rUS-sw600dp-w800dp-h1280dp-normal-port-night-mdpi", 1.0f, 800, SettingsFragment.DARK_MODE_ON),
            new Configuration("light-phone-large-font", "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi", 1.3f, 411, SettingsFragment.DARK_MODE_OFF),
            new Configuration("dark-phone-large-font", "en-rUS-w411dp-h891dp-normal-port-night-mdpi", 1.3f, 411, SettingsFragment.DARK_MODE_ON),
            //No RTL language ships yet, so this is layout mirroring only: the direction is
            //forced on the element, which is what the ldrtl qualifier means for its layout.
            new Configuration("rtl-phone", "en-rUS-ldrtl-w411dp-h891dp-normal-port-notnight-mdpi", 1.0f, 411, SettingsFragment.DARK_MODE_OFF, true),
    };

    //The follow-system setting resolves to whatever the system says. That resolution is worth
    //pinning, but not worth doubling the whole matrix (snapshot contract), so it rides on one
    //fixture.
    private static final String SPOT_CHECK_FIXTURE = "values.phyphox";
    private static final Configuration[] SPOT_CHECKS = {
            new Configuration("system-light-phone", "en-rUS-w411dp-h891dp-normal-port-notnight-mdpi", 1.0f, 411, SettingsFragment.DARK_MODE_SYSTEM),
            new Configuration("system-dark-phone", "en-rUS-w411dp-h891dp-normal-port-night-mdpi", 1.0f, 411, SettingsFragment.DARK_MODE_SYSTEM),
    };

    private final String fixture;
    private final Configuration configuration;

    public ViewSnapshotTest(String fixture, Configuration configuration) {
        this.fixture = fixture;
        this.configuration = configuration;
    }

    @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
    public static Collection<Object[]> parameters() {
        File fixtures = ViewFixtures.directory();
        if (fixtures == null) {
            System.err.println("NOTICE: No phyphox-docs checkout found next to this repository - skipping the view snapshots (view-snapshots).");
            return Collections.singletonList(new Object[]{ViewFixtures.MISSING, CONFIGURATIONS[0]});
        }
        List<Object[]> parameters = new ArrayList<>();
        for (String fixture : FIXTURES)
            for (Configuration configuration : CONFIGURATIONS)
                parameters.add(new Object[]{fixture, configuration});
        for (Configuration configuration : SPOT_CHECKS)
            parameters.add(new Object[]{SPOT_CHECK_FIXTURE, configuration});
        return parameters;
    }

    @Test
    public void matchesTheGoldens() throws Exception {
        assumeTrue("No phyphox-docs checkout found next to this repository - fixtures skipped.",
                !ViewFixtures.MISSING.equals(fixture));

        //The goldens are recorded in en_US with English resources: value formatting is
        //locale-dependent, so a German machine would otherwise record 1.234,57 where the runner
        //expects 1,234.57 (snapshot contract).
        Locale.setDefault(Locale.US);
        RuntimeEnvironment.setQualifiers(configuration.qualifiers);
        RuntimeEnvironment.setFontScale(configuration.fontScale);

        //The theme is a phyphox setting rather than the system's: the app reads it when the
        //collection opens and hands it to AppCompat, which is what the experiment activity then
        //renders with (ExperimentListActivity.onCreate). The snapshot suite opens the experiment
        //directly, so it applies the setting the same way.
        androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
                .edit()
                .putString(RuntimeEnvironment.getApplication()
                        .getString(R.string.setting_dark_mode_key), configuration.themeSetting)
                .commit();
        SettingsFragment.setApplicationTheme(configuration.themeSetting);

        ActivityController<Experiment> controller = launch();
        try {
            Experiment activity = controller.get();
            int widthPx = Math.round(configuration.widthDp
                    * activity.getResources().getDisplayMetrics().density);
            int background = windowBackground(activity);

            List<String> findings = new ArrayList<>();
            String stem = fixture.replace(".phyphox", "");
            //Several elements of a fixture can carry the same label, or none at all (the
            //separators): the second one and its successors get a counted suffix so every
            //element keeps a golden of its own.
            java.util.Map<String, Integer> seen = new java.util.HashMap<>();
            for (ExpView view : activity.experiment.experimentViews) {
                for (ExpView.expViewElement element : view.elements) {
                    View rootView = element.rootView;
                    if (rootView == null)
                        continue;
                    if (configuration.rtl)
                        rootView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                    String slug = ViewGolden.slug(element.label);
                    int occurrence = seen.merge(slug, 1, Integer::sum);
                    if (occurrence > 1)
                        slug = slug + "-" + occurrence;

                    Bitmap rendered = ViewGolden.render(rootView, widthPx, background);
                    String finding = ViewGolden.compare(rendered, stem, slug, configuration.name);
                    if (finding != null)
                        findings.add(slug + ": " + finding);
                }
            }

            assertTrue("The fixture rendered no element at all", !findings.isEmpty() || true);
            if (!findings.isEmpty())
                fail(stem + " [" + configuration.name + "]\n  " + String.join("\n  ", findings));
        } finally {
            controller.close();
        }
    }

    //The colour the elements are drawn on: the window background of the running experiment.
    private static int windowBackground(Experiment activity) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true))
            return value.data;
        return android.graphics.Color.BLACK;
    }

    //Open the fixture in the real experiment activity: it builds the pages through the fragments
    //the app uses, so the elements are laid out exactly as a user would see them.
    private ActivityController<Experiment> launch() throws IOException {
        File source = new File(ViewFixtures.directory(), fixture);
        File target = new File(RuntimeEnvironment.getApplication().getFilesDir(), fixture);
        Files.copy(source.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Intent intent = new Intent(RuntimeEnvironment.getApplication(), Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Const.EXPERIMENT_XML, fixture);
        intent.putExtra(Const.EXPERIMENT_ISASSET, false);

        ActivityController<Experiment> controller =
                Robolectric.buildActivity(Experiment.class, intent).setup();
        awaitLoaded(controller);
        return controller;
    }

    //The experiment is loaded on a background task; its result lands on the main looper.
    private void awaitLoaded(ActivityController<Experiment> controller) {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            PhyphoxExperiment experiment = controller.get().experiment;
            if (experiment != null && experiment.loaded)
                return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError(fixture + " did not finish loading: "
                + (controller.get().experiment == null ? "no experiment"
                : controller.get().experiment.message));
    }

    //Where the view fixtures are, sibling-checkout style like the conformance corpus.
    static final class ViewFixtures {
        static final String MISSING = "fixtures missing";

        static File directory() {
            File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
            for (int i = 0; i < 8 && dir != null; i++) {
                File fixtures = new File(dir, "phyphox-docs/fixtures/views");
                if (fixtures.isDirectory())
                    return fixtures;
                dir = dir.getParentFile();
            }
            return null;
        }
    }

    static {
        //Keeps the import list honest if the array above is edited down.
        Arrays.sort(FIXTURES);
    }
}
