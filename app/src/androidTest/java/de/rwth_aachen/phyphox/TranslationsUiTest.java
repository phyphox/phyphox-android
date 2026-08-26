package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
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
    private String tagOf(String qualifier) {
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

    @Test
    public void everyEnabledLanguageRendersItsScreens() throws Exception {
        List<String> findings = new ArrayList<>();
        List<String> unrendered = new ArrayList<>();

        for (String qualifier : BuildConfig.LOCALE_ARRAY) {
            String language = tagOf(qualifier);
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

        Log.i(TAG, findings.size() + " truncated labels over "
                + BuildConfig.LOCALE_ARRAY.length + " languages");
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
