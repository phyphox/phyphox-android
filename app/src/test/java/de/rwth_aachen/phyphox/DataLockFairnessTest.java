package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

//A reader of the buffers (remote /get and /export, the state writer) must not be starved by the
//analysis and sensor callbacks, which re-take the data lock immediately after releasing it; with
//a non-fair lock nothing bounds how often they barge past a waiter. The data lock must be fair.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DataLockFairnessTest {

    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Data lock</title>"
                    + "<category>Test</category>"
                    + "<description>Minimal experiment, used only for its data lock.</description>"
                    + "<data-containers><container size=\"8\" init=\"1\">a</container></data-containers>"
                    + "<views><view label=\"View\"><value label=\"v\"><input>a</input></value></view></views>"
                    + "</phyphox>";

    private static final long BUDGET_MS = 1000; //generous: a fair lock gets in within milliseconds

    private static final int HAMMERS = 3; //one barging thread does not reliably starve a waiter

    private volatile boolean hammering = true;

    @Test
    public void aReaderGetsTheDataLockWhileTheAnalysisKeepsTakingIt() throws Exception {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
                new ByteArrayInputStream(EXPERIMENT.getBytes(StandardCharsets.UTF_8)), activity);
        assertTrue("Test experiment failed to load: " + experiment.message, experiment.loaded);

        List<Thread> hammers = new ArrayList<>();
        for (int i = 0; i < HAMMERS; i++) {
            Thread hammer = new Thread(() -> {
                while (hammering) {
                    experiment.dataLock.lock();
                    try {
                        busyFor(2);
                    } finally {
                        experiment.dataLock.unlock();
                    }
                }
            });
            hammer.setDaemon(true);
            hammers.add(hammer);
        }

        try {
            for (Thread hammer : hammers)
                hammer.start();
            Thread.sleep(150); //let the hammers settle so the reader arrives at a contended lock

            long start = System.nanoTime();
            boolean acquired = experiment.dataLock.tryLock(BUDGET_MS, TimeUnit.MILLISECONDS);
            long waited = (System.nanoTime() - start) / 1000000;
            if (acquired)
                experiment.dataLock.unlock();

            assertTrue("a reader did not get the data lock within " + waited + " ms while the "
                    + "analysis kept taking it - a remote /get or an export would time out",
                    acquired);
        } finally {
            hammering = false;
            for (Thread hammer : hammers)
                hammer.join(5000);
        }
    }

    private static void busyFor(long millis) {
        long deadline = System.nanoTime() + millis * 1000000L;
        while (System.nanoTime() < deadline)
            ;
    }
}
