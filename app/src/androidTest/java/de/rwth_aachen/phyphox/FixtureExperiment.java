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

//Opens one of phyphox-docs' view fixtures (fixtures/views/, built into this test APK's assets by
//app/build.gradle) in the real Experiment activity. The file is copied into the app's private
//directory and opened with the intent the experiment list itself uses, so the fixture arrives
//through the normal loading path - no server, no storage permission, no picker.
//
//The fixtures render deterministically: all data comes from container init values, no sensors,
//no analysis, and they are never started.
final class FixtureExperiment {

    private FixtureExperiment() {
    }

    //True when the phyphox-docs checkout was present at build time; the suites skip themselves
    //otherwise, the way the corpus tests do.
    static boolean available(String fixture) {
        try (InputStream ignored = getInstrumentation().getContext().getAssets().open(fixture)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    //Starts the fixture and returns the running activity once its experiment is loaded.
    //
    //Deliberately not ActivityScenario: everything it does waits for the main looper to go idle,
    //and a running experiment redraws its views every 40 ms for as long as it is open, so the
    //looper never does and the test hangs where it launches. Starting the activity and watching
    //the lifecycle monitor waits for nothing but the app itself. Espresso interactions are
    //unaffected - it ignores messages scheduled further than a few milliseconds ahead.
    static Experiment launch(String fixture) throws IOException {
        Context app = getInstrumentation().getTargetContext();
        copyToPrivateDir(fixture, app);

        Intent intent = new Intent(app, Experiment.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(Const.EXPERIMENT_XML, fixture);
        intent.putExtra(Const.EXPERIMENT_ISASSET, false);
        app.startActivity(intent);
        return awaitLoaded();
    }

    //Closes the experiment again, so the next fixture starts from the collection.
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

    //The running experiment activity, without going through ActivityScenario.onActivity.
    //
    //A running experiment redraws its views every 40 ms for as long as it is open, so the main
    //looper is never idle - and everything built on waitForIdleSync (onActivity among them)
    //waits for exactly that and never returns. runOnMainSync only waits for its own runnable, so
    //it works on a screen that keeps itself busy. Espresso is fine either way: it ignores
    //messages scheduled further than a few milliseconds into the future.
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

    //Waits until the experiment has finished loading, which happens on a background task.
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
