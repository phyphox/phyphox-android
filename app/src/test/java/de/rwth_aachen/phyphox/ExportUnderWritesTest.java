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

//Exporting a running experiment copies buffers the analysis keeps writing; the snapshot must be
//taken under the data lock. The buffers are bounded so the writer appends and drops values
//without growing the heap.
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

        //the writer holds the lock exactly as the analysis does
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
