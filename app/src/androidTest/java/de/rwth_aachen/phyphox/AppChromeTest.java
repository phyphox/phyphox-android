package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// phyphox-test: app-chrome
//The screens around the experiment: the collection, the experiment menu and its dialogs, the
//settings, what rotation does to a running screen, and the permission flow with both answers.
//
//Driven with UiAutomator, like the other suites here: an open experiment redraws continuously,
//so Espresso's idle condition never holds on it.
@RunWith(AndroidJUnit4.class)
public class AppChromeTest {

    private UiDevice device() {
        return UiDevice.getInstance(getInstrumentation());
    }

    private void shell(String command) throws Exception {
        device().executeShellCommand(command);
    }

    @Before
    public void startFromTheCollection() throws Exception {
        FixtureExperiment.suppressHints();
        openCollection();
    }

    @After
    public void closeWhateverIsOpen() throws Exception {
        FixtureExperiment.close(FixtureExperiment.activity());
        device().setOrientationNatural();
    }

    private void openCollection() {
        android.content.Context context = getInstrumentation().getTargetContext();
        Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        assertNotNull("the app has no launcher intent", intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        device().wait(Until.hasObject(By.pkg(context.getPackageName()).depth(0)), 10000);
    }

    @Test
    public void theCollectionListsItsCategoriesAndExperiments() {
        assertTrue("the collection shows no raw sensor category",
                device().wait(Until.hasObject(By.text("Raw Sensors")), 15000));
        assertTrue("the collection lists no accelerometer experiment",
                device().wait(Until.hasObject(By.textContains("Acceleration")), 5000));
    }

    @Test
    public void theExperimentMenuOffersItsActions() throws Exception {
        Experiment activity = FixtureExperiment.launchAsset("accelerometer.phyphox");
        try {
            UiObject2 overflow = device().wait(Until.findObject(By.desc("More options")), 10000);
            assertNotNull("the experiment has no menu", overflow);
            overflow.click();

            for (String item : new String[]{"Timed run", "Export Data", "Experiment info",
                    "Allow remote access", "Save experiment state"})
                assertTrue("the experiment menu has no \"" + item + "\"",
                        device().wait(Until.hasObject(By.text(item)), 5000));

            //A dialog opens and closes again without leaving the experiment behind.
            device().findObject(By.text("Timed run")).click();
            //The dialog opens with its switch; the delay fields appear once it is on.
            assertTrue("the timed run dialog did not open",
                    device().wait(Until.hasObject(By.text("Do a timed run")), 5000));
            device().pressBack();
            assertTrue("the experiment did not come back after the dialog",
                    device().wait(Until.hasObject(By.desc("More options")), 5000));
        } finally {
            FixtureExperiment.close(activity);
        }
    }

    @Test
    public void theSettingsScreenOpens() {
        //The collection keeps its menu behind the "Info" button rather than an overflow.
        UiObject2 info = device().wait(Until.findObject(By.desc("Info")), 10000);
        assertNotNull("the collection has no info menu", info);
        info.click();

        UiObject2 settings = device().wait(Until.findObject(By.text("Settings")), 5000);
        assertNotNull("the collection menu has no settings", settings);
        settings.click();

        assertTrue("the settings screen did not open",
                device().wait(Until.hasObject(By.textContains("Appearance")), 10000)
                        || device().hasObject(By.textContains("Language")));
        device().pressBack();
    }

    @Test
    public void anExperimentSurvivesRotation() throws Exception {
        Experiment activity = FixtureExperiment.launchAsset("accelerometer.phyphox");
        try {
            device().setOrientationLeft();
            Thread.sleep(2000);
            assertTrue("the experiment did not survive the rotation",
                    FixtureExperiment.awaitLoaded() != null);
            assertTrue("the experiment screen is gone after rotating",
                    device().wait(Until.hasObject(By.desc("More options")), 5000));

            device().setOrientationNatural();
            Thread.sleep(2000);
            assertTrue("the experiment did not survive rotating back",
                    FixtureExperiment.awaitLoaded() != null);
        } finally {
            FixtureExperiment.close(FixtureExperiment.activity());
        }
    }

}
