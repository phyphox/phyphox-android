package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// phyphox-test: translations-ui
//Every language the build enables gets its screens rendered: the collection, an experiment, and
//the experiment's menu. What this looks for is text that does not fit - a label cut off with an
//ellipsis is how a translation regression shows up long before anyone reports it.
//
//Rendering is asserted (a language whose screens do not come up is a failure); the truncations
//themselves are reported under the tag phyphoxI18n, the way accessibility-smoke reports its
//findings, until they are triaged and the row escalates to failing on new ones.
//
//    adb logcat -s phyphoxI18n
@RunWith(AndroidJUnit4.class)
public class TranslationsUiTest {

    private static final String TAG = "phyphoxI18n";

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    @Before
    public void quietFirstRunDialogs() {
        FixtureExperiment.suppressHints();
    }

    @After
    public void backToTheBuildLanguage() {
        setLanguage(null);
        FixtureExperiment.close(FixtureExperiment.activity());
    }

    //The app's own per-app language, which is what a user picks in the settings.
    private void setLanguage(String tag) {
        getInstrumentation().runOnMainSync(() -> AppCompatDelegate.setApplicationLocales(
                tag == null ? LocaleListCompat.getEmptyLocaleList()
                        : LocaleListCompat.forLanguageTags(tag)));
        getInstrumentation().waitForIdleSync();
    }

    //Android resource qualifiers into the language tags AppCompat wants.
    private static String tagOfQualifier(String qualifier) {
        if (qualifier.startsWith("b+"))
            return qualifier.substring(2).replace('+', '-');
        return qualifier.replace("-r", "-");
    }

    private Activity resumedActivity() {
        final Activity[] holder = new Activity[1];
        getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED))
                holder[0] = activity;
        });
        return holder[0];
    }

    //Text that had to be cut to fit, and views whose text is wider than they are.
    private List<String> truncations(View root, String screen, String language) {
        List<String> findings = new ArrayList<>();
        collect(root, screen, language, findings);
        return findings;
    }

    private void collect(View view, String screen, String language, List<String> findings) {
        if (view instanceof TextView && isLabel((TextView) view)) {
            TextView text = (TextView) view;
            Layout layout = text.getLayout();
            if (layout != null && text.getVisibility() == View.VISIBLE) {
                for (int line = 0; line < layout.getLineCount(); line++) {
                    if (layout.getEllipsisCount(line) > 0) {
                        findings.add(String.format(Locale.US, "%s/%s: \"%s\" is cut off",
                                language, screen, text.getText()));
                        break;
                    }
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                collect(group.getChildAt(i), screen, language, findings);
        }
    }

    //A label is what must fit: a title, a button, a menu entry. The collection's experiment
    //summaries (expInfo) are one-line teasers of a paragraph and are cut on purpose in every
    //language, English included - flagging those would bury the regressions this looks for.
    private boolean isLabel(TextView text) {
        if (text.getId() == R.id.expInfo)
            return false;
        return text.getMaxLines() <= 1 && text.getText() != null && text.getText().length() <= 60;
    }

    private void openCollection() {
        android.content.Context context = getInstrumentation().getTargetContext();
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        device().wait(Until.hasObject(By.pkg(context.getPackageName()).depth(0)), 10000);
    }

    //Which languages this run covers, as language tags, sorted.
    //
    //The sweep is the longest thing in T1, so it can be split across jobs - by a convention both
    //platforms share, or the shards would not be comparable (test-matrix row translations-ui):
    //sort the build's language set, then either take an explicit subset or every n-th entry.
    //Sorting is done on the language TAG rather than on Android's resource qualifier, because the
    //tag is the form iOS can produce the same order from.
    //
    //Android has no environment to read - an instrumented test runs in the app's process, which
    //does not inherit the shell's - so the two names arrive as instrumentation arguments:
    //
    //    adb shell am instrument -e PHYPHOX_TEST_LANGUAGE_SHARD 1/2 ...
    //    adb shell am instrument -e PHYPHOX_TEST_LANGUAGES de,fr ...
    //
    //An actual environment variable is honoured too, for a runner that can set one.
    static List<String> languagesUnderTest(String[] enabledQualifiers, String subsetSpec,
                                           String shardSpec) {
        List<String> all = new ArrayList<>();
        for (String qualifier : enabledQualifiers)
            all.add(tagOfQualifier(qualifier));
        Collections.sort(all);

        if (subsetSpec != null && !subsetSpec.trim().isEmpty()) {
            List<String> wanted = new ArrayList<>();
            for (String code : subsetSpec.split(",")) {
                String tag = code.trim();
                if (tag.isEmpty())
                    continue;
                //A typo must not quietly remove coverage, so an unknown code fails the run.
                if (!all.contains(tag))
                    throw new AssertionError("PHYPHOX_TEST_LANGUAGES names \"" + tag
                            + "\", which this build does not enable. Enabled: " + all);
                wanted.add(tag);
            }
            if (wanted.isEmpty())
                throw new AssertionError("PHYPHOX_TEST_LANGUAGES is set but names no language");
            return wanted;
        }

        if (shardSpec != null && !shardSpec.trim().isEmpty()) {
            String[] parts = shardSpec.trim().split("/");
            int index, count;
            try {
                if (parts.length != 2)
                    throw new NumberFormatException();
                index = Integer.parseInt(parts[0].trim());
                count = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new AssertionError("PHYPHOX_TEST_LANGUAGE_SHARD takes i/n, e.g. 1/2, not \""
                        + shardSpec + "\"");
            }
            if (count < 1 || index < 1 || index > count)
                throw new AssertionError("PHYPHOX_TEST_LANGUAGE_SHARD " + shardSpec
                        + ": i must be within 1..n");
            List<String> shard = new ArrayList<>();
            //Round-robin rather than slicing, so the shards cost about the same.
            for (int i = index - 1; i < all.size(); i += count)
                shard.add(all.get(i));
            if (shard.isEmpty())
                throw new AssertionError("PHYPHOX_TEST_LANGUAGE_SHARD " + shardSpec
                        + " is empty - fewer languages (" + all.size() + ") than shards?");
            return shard;
        }

        return all;
    }

    private static String argument(String name) {
        String value = null;
        try {
            value = InstrumentationRegistry.getArguments().getString(name);
        } catch (Exception e) {
            //No instrumentation arguments available - fall through to the environment
        }
        return value != null ? value : System.getenv(name);
    }

    //The selection convention itself, which is what has to match iOS. Pure logic, so it costs
    //nothing to run alongside the sweep, and it is the part a shard split gets wrong silently.
    @Test
    public void languageSelectionFollowsTheSharedConvention() {
        String[] enabled = {"de", "b+zh+Hans", "fr", "pt-rBR", "el"};

        //Sorted by language tag, which is the order both platforms can produce.
        assertEquals("[de, el, fr, pt-BR, zh-Hans]",
                languagesUnderTest(enabled, null, null).toString());

        //Round-robin, so the shards cost about the same, and together they cover everything once.
        List<String> first = languagesUnderTest(enabled, null, "1/2");
        List<String> second = languagesUnderTest(enabled, null, "2/2");
        assertEquals("[de, fr, zh-Hans]", first.toString());
        assertEquals("[el, pt-BR]", second.toString());
        List<String> union = new ArrayList<>(first);
        union.addAll(second);
        Collections.sort(union);
        assertEquals(languagesUnderTest(enabled, null, null).toString(), union.toString());

        //An explicit subset wins over a shard and keeps the order it was given.
        assertEquals("[fr, de]", languagesUnderTest(enabled, "fr, de", "1/2").toString());

        //A typo must fail rather than quietly shrink the coverage.
        assertRefused(() -> languagesUnderTest(enabled, "de,xx", null), "xx");
        assertRefused(() -> languagesUnderTest(enabled, "  ", "9"), "i/n");
        assertRefused(() -> languagesUnderTest(enabled, null, "3/2"), "within 1..n");
        //Round-robin means shard i is empty only once i is past the end of the list.
        assertEquals("[de]", languagesUnderTest(enabled, null, "1/99").toString());
        assertRefused(() -> languagesUnderTest(enabled, null, "99/99"), "empty");
    }

    private void assertRefused(Runnable selection, String expectedInMessage) {
        try {
            selection.run();
        } catch (AssertionError e) {
            assertTrue("refused for the wrong reason: " + e.getMessage(),
                    e.getMessage().contains(expectedInMessage));
            return;
        }
        throw new AssertionError("the selection was accepted although it names " + expectedInMessage);
    }

    @Test
    public void everyEnabledLanguageRendersItsScreens() throws Exception {
        List<String> findings = new ArrayList<>();
        List<String> unrendered = new ArrayList<>();

        List<String> languages = languagesUnderTest(BuildConfig.LOCALE_ARRAY,
                argument("PHYPHOX_TEST_LANGUAGES"), argument("PHYPHOX_TEST_LANGUAGE_SHARD"));
        Log.i(TAG, "covering " + languages.size() + " of " + BuildConfig.LOCALE_ARRAY.length
                + " enabled languages: " + String.join(",", languages));

        for (String language : languages) {
            setLanguage(language);

            openCollection();
            Thread.sleep(1500);
            Activity collection = resumedActivity();
            if (collection == null || !hasVisibleText(collection)) {
                unrendered.add(language + ": the collection did not render");
                continue;
            }
            final Activity collectionActivity = collection;
            getInstrumentation().runOnMainSync(() -> findings.addAll(
                    truncations(collectionActivity.getWindow().getDecorView(), "collection",
                            language)));

            //A shipped experiment, whose title, description and menu are translated - the
            //fixtures are English on purpose and would prove nothing here.
            Experiment experiment = FixtureExperiment.launchAsset("accelerometer.phyphox");
            Thread.sleep(1000);
            if (experiment == null || !experiment.experiment.loaded) {
                unrendered.add(language + ": the experiment did not open");
                continue;
            }
            getInstrumentation().runOnMainSync(() -> findings.addAll(
                    truncations(experiment.getWindow().getDecorView(), "experiment", language)));

            //And the menu, where the longest strings live.
            openOverflow();
            Thread.sleep(800);
            Activity menuOwner = resumedActivity();
            if (menuOwner != null) {
                final Activity owner = menuOwner;
                getInstrumentation().runOnMainSync(() -> findings.addAll(
                        truncations(owner.getWindow().getDecorView(), "menu", language)));
            }
            device().pressBack();
            FixtureExperiment.close(FixtureExperiment.activity());
        }

        Log.i(TAG, findings.size() + " truncated labels over " + languages.size() + " languages");
        for (String finding : findings)
            Log.i(TAG, finding);

        assertTrue(String.join("\n  ", unrendered), unrendered.isEmpty());
    }

    private void openOverflow() {
        //The overflow's description is itself translated, so it is found by position: the last
        //button of the action bar.
        getInstrumentation().runOnMainSync(() -> {
            Activity activity = resumedActivityOnMainThread();
            if (activity != null)
                activity.openOptionsMenu();
        });
    }

    private Activity resumedActivityOnMainThread() {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED))
            return activity;
        return null;
    }

    private boolean hasVisibleText(Activity activity) {
        final boolean[] found = new boolean[1];
        getInstrumentation().runOnMainSync(() ->
                found[0] = hasText(activity.getWindow().getDecorView()));
        return found[0];
    }

    private boolean hasText(View view) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE
                && ((TextView) view).getText().length() > 0)
            return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (hasText(group.getChildAt(i)))
                    return true;
        }
        return false;
    }
}
