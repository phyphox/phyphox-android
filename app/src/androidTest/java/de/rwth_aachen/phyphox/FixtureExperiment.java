package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.rwth_aachen.phyphox.ExperimentList.model.Const;

//Opens one of phyphox-docs' view fixtures (fixtures/views/, packed into the test APK's assets by
//app/build.gradle) in the real Experiment activity through the normal loading path.
final class FixtureExperiment {

    private FixtureExperiment() {
    }

    //False when phyphox-docs was not checked out at build time; the suites skip themselves then.
    static boolean available(String fixture) {
        try (InputStream ignored = getInstrumentation().getContext().getAssets().open(fixture)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    //Set before launching: AppCompat resolves the night mode when the activity is created.
    static void applyThemeSetting(String setting) {
        Context app = getInstrumentation().getTargetContext();
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
                .edit()
                .putString(app.getString(R.string.setting_dark_mode_key), setting)
                .commit();
        getInstrumentation().runOnMainSync(() ->
                de.rwth_aachen.phyphox.SettingsActivity.SettingsFragment.setApplicationTheme(setting));
    }

    //The one-off hints float over the screen and would land in every golden and under every tap.
    static void suppressHints() {
        getInstrumentation().getTargetContext()
                .getSharedPreferences(de.rwth_aachen.phyphox.ExperimentList.model.Const.PREFS_NAME, 0)
                .edit()
                .putInt("menuHintDismissCount", 3)
                .putInt("startHintDismissCount", 3)
                //the fresh-install warning dialog's "do not show again"
                .putBoolean("skipWarning", true)
                .commit();
    }

    //Starts the fixture and returns the activity once its experiment is loaded. Not ActivityScenario:
    //a running experiment redraws every 40 ms, so the main looper never idles and it would hang.
    static Experiment launch(String fixture) throws IOException {
        Context app = getInstrumentation().getTargetContext();
        suppressHints();
        copyToPrivateDir(fixture, app);

        Intent intent = new Intent(app, Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(Const.EXPERIMENT_XML, fixture);
        intent.putExtra(Const.EXPERIMENT_ISASSET, false);
        app.startActivity(intent);
        return awaitLoaded();
    }

    //The emulator's alias for the machine running the tests, which serves fixtures over http.
    static String hostFromDevice() {
        return "10.0.2.2:8115";
    }

    static Experiment launchAsset(String asset) {
        launchAssetWithoutWaiting(asset);
        return awaitLoaded();
    }

    //For the cases that expect something other than a loaded experiment, e.g. a permission dialog.
    static void launchAssetWithoutWaiting(String asset) {
        Context app = getInstrumentation().getTargetContext();
        suppressHints();
        Intent intent = new Intent(app, Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(Const.EXPERIMENT_XML, asset);
        intent.putExtra(Const.EXPERIMENT_ISASSET, true);
        app.startActivity(intent);
    }

    //Without recreating anything, the way tapping the icon does; relaunching would start afresh.
    static void bringToForeground() {
        Context app = getInstrumentation().getTargetContext();
        Intent intent = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (intent == null)
            throw new AssertionError("the app has no launcher intent");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        app.startActivity(intent);
    }

    static void close(Experiment activity) {
        if (activity != null)
            getInstrumentation().runOnMainSync(activity::finish);
    }

    private static void copyToPrivateDir(String fixture, Context app) throws IOException {
        try (InputStream in = getInstrumentation().getContext().getAssets().open(fixture);
             OutputStream out = new FileOutputStream(new File(app.getFilesDir(), fixture))) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1)
                out.write(buffer, 0, n);
        }
    }

    //Via runOnMainSync, not ActivityScenario.onActivity: the main looper never idles (see launch).
    static Experiment activity() {
        final Experiment[] holder = new Experiment[1];
        getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (activity instanceof Experiment)
                    holder[0] = (Experiment) activity;
            }
        });
        return holder[0];
    }

    static Experiment awaitLoaded() {
        final long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            Experiment activity = activity();
            if (activity != null && activity.experiment != null && activity.experiment.loaded)
                return activity;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("The fixture did not finish loading");
    }
}
