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

//A reader of the buffers must not be starved by the analysis.
//
//The remote server's /get and /export, the exporter and the state writer all take the
//experiment's data lock, and so do the analysis and the sensor callbacks - the latter over and
//over, several times per analysis pass with nothing in between. With a non-fair lock a thread
//that re-acquires immediately may barge past a waiter, and nothing bounds how often that can
//happen: the lab's Galaxy A3 answered /config instantly while a 167-byte /get went unanswered
//for minutes and all six exports of doppler failed, on a phone slow enough that the analysis
//never left a gap for the reader to slip into.
//
//This reproduces the shape rather than the device: a few threads holding the lock briefly and
//re-taking it at once, and a reader that has to get in. Against a non-fair lock the reader waits
//seconds - this same test measured 120 seconds before the reader was let through - while a fair
//lock lets it in after the holds already queued ahead of it, single-digit milliseconds. The wait
//is bounded rather than open-ended so a regression fails in a second instead of hanging.
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

    //Generous: the fair lock gets in after the holds already queued ahead of the reader, so a few
    //milliseconds. The non-fair lock never managed better than 1.4 seconds in the same shape.
    private static final long BUDGET_MS = 1000;

    //Three, because one barging thread does not reliably starve a waiter and the point is a pin
    //that fails when the lock stops being fair, not one that usually does.
    private static final int HAMMERS = 3;

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
            //Let them settle into the loop, so the reader really arrives at a contended lock.
            Thread.sleep(150);

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
