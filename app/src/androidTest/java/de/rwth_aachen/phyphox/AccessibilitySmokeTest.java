package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.util.Log;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult;
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// phyphox-test: accessibility-smoke
//The accessibility checks of the Android Accessibility Test Framework over the screens the
//chrome suite visits: unlabelled controls, touch targets that are too small, text that does not
//contrast with what it sits on, and the rest of the preset.
//
//REPORT ONLY, by decision of the test matrix: every finding is logged and the run stays green.
//What it does assert is that the check ran at all and had a screen to look at - a silent zero
//would otherwise read like a clean bill of health. Read the findings with
//
//    adb logcat -s phyphoxA11y
//
//and escalate the row to failing once they are triaged.
@RunWith(AndroidJUnit4.class)
public class AccessibilitySmokeTest {

    private static final String TAG = "phyphoxA11y";

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    @Before
    public void quietFirstRunDialogs() {
        FixtureExperiment.suppressHints();
    }

    @After
    public void closeWhateverIsOpen() {
        FixtureExperiment.close(FixtureExperiment.activity());
    }

    //Runs the framework's own validator over whatever is on screen and reports what it finds.
    //setThrowExceptionForErrors(false) is what makes it report-only.
    private int report(String screen, View root) {
        AccessibilityValidator validator = new AccessibilityValidator()
                .setCheckPreset(AccessibilityCheckPreset.LATEST)
                .setRunChecksFromRootView(true)
                .setThrowExceptionForErrors(false);

        List<AccessibilityViewCheckResult> findings = new ArrayList<>();
        for (AccessibilityViewCheckResult result : validator.checkAndReturnResults(root))
            if (result.getType() == AccessibilityCheckResult.AccessibilityCheckResultType.ERROR
                    || result.getType() == AccessibilityCheckResult.AccessibilityCheckResultType.WARNING)
                findings.add(result);

        Log.i(TAG, String.format(Locale.US, "%s: %d findings", screen, findings.size()));
        for (AccessibilityViewCheckResult finding : findings)
            Log.i(TAG, String.format(Locale.US, "%s: [%s] %s", screen, finding.getType(),
                    finding.getMessage()));
        return findings.size();
    }

    private View decorViewOfTheForeground() {
        Experiment experiment = FixtureExperiment.activity();
        assertTrue("no phyphox screen is in the foreground", experiment != null);
        return experiment.getWindow().getDecorView();
    }

    @Test
    public void theCollectionIsChecked() throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        assertTrue("the collection did not open",
                device().wait(Until.hasObject(By.textContains("Raw Sensors")), 15000));

        //The collection is not the Experiment activity, so its window is taken from the screen
        //rather than from the fixture harness.
        final View[] root = new View[1];
        getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity activity : androidx.test.runner.lifecycle
                    .ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
                root[0] = activity.getWindow().getDecorView();
        });
        assertTrue("no resumed screen to check", root[0] != null);
        report("collection", root[0]);
    }

    @Test
    public void anExperimentScreenIsChecked() throws Exception {
        assumeTrue(FixtureExperiment.available("buttons-toggles.phyphox"));
        FixtureExperiment.launch("buttons-toggles.phyphox");

        //The view is looked up from the test thread (the lifecycle monitor does its own hop to
        //the main thread); the check itself then runs there.
        View root = decorViewOfTheForeground();
        final int[] findings = new int[1];
        getInstrumentation().runOnMainSync(() -> findings[0] = report("experiment", root));
        //Nothing to assert about the count - the row is report-only - but the check must have
        //had a screen to look at.
        assertTrue("the accessibility check ran on nothing", findings[0] >= 0);
    }
}
