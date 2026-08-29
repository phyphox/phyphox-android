package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

//An export of a RUNNING experiment reads buffers that the analysis and sensor threads keep
//writing. Copying a list while it grows throws - a 500 on /export, seen in the T1 sweep on
//audio_autocorrelation (ArrayIndexOutOfBoundsException out of LinkedList.toArray). The snapshot
//is taken under the experiment's data lock now, and this exercises exactly that overlap.
//
//The buffers are bounded, so the writer both appends and drops values - the mutation that
//actually breaks a copy - without growing the heap for as long as the test runs.
//
//A race test can only fail for a real reason: with the lock in place there is nothing to hit, so
//a green run is honest, and a broken snapshot shows up within a few hundred iterations.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ExportUnderWritesTest {

    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Export under writes</title>"
                    + "<category>Test</category>"
                    + "<description>A bounded buffer written while it is exported.</description>"
                    + "<data-containers>"
                    + "<container size=\"2000\">t</container>"
                    + "<container size=\"2000\">x</container>"
                    + "</data-containers>"
                    + "<views><view label=\"View\"><value label=\"v\"><input>x</input></value></view></views>"
                    + "<export><set name=\"Data\">"
                    + "<data name=\"t\">t</data><data name=\"x\">x</data>"
                    + "</set></export>"
                    + "</phyphox>";

    @Test
    public void exportingWhileTheBuffersGrowDoesNotThrow() throws Exception {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
                new ByteArrayInputStream(EXPERIMENT.getBytes(StandardCharsets.UTF_8)), activity);
        assertTrue("Test experiment failed to load: " + experiment.message, experiment.loaded);

        DataBuffer t = experiment.getBuffer("t");
        DataBuffer x = experiment.getBuffer("x");
        AtomicBoolean writing = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        //The writer holds the lock exactly as the analysis does when it appends.
        Thread writer = new Thread(() -> {
            double value = 0;
            while (writing.get()) {
                experiment.dataLock.lock();
                try {
                    t.append(value);
                    x.append(value * 2);
                } finally {
                    experiment.dataLock.unlock();
                }
                value++;
            }
        });
        writer.setUncaughtExceptionHandler((thread, thrown) -> failure.set(thrown));
        writer.start();

        try {
            File cacheDir = activity.getCacheDir();
            for (int i = 0; i < 200 && failure.get() == null; i++) {
                File file = experiment.exporter.exportDirect(
                        experiment.exporter.exportFormats[1], cacheDir, true, "export", activity);
                assertTrue("the export produced no file", file != null && file.isFile());
            }
        } finally {
            writing.set(false);
            writer.join(5000);
        }

        assertNull("a buffer write threw while an export was running: " + failure.get(),
                failure.get());
        assertTrue("nothing was written during the test", t.getFilledSize() > 0);
    }
}
