package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

///get against a real RemoteServer: the update modes, the threshold filter, the y=<threshold>|x
//reference form, and that the data lock is not held while the answer is formatted.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RemoteServerGetTest {

    private static final int BIG = 250000; //large enough that formatting dominates the response

    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Get endpoint test</title>"
                    + "<category>Test</category>"
                    + "<description>Minimal experiment for the /get endpoint test.</description>"
                    + "<data-containers>"
                    + "<container size=\"" + BIG + "\">big</container>"
                    + "<container size=\"8\">small</container>"
                    + "<container size=\"8\">ref</container>"
                    + "</data-containers>"
                    + "<views><view label=\"View\"><value label=\"v\"><input>small</input></value></view></views>"
                    + "</phyphox>";

    private PhyphoxExperiment experiment;
    private RemoteServer server;
    private String base;

    @Before
    public void startServer() {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        experiment = CorpusTestEnvironment.load(
                new ByteArrayInputStream(EXPERIMENT.getBytes(StandardCharsets.UTF_8)), activity);
        assertTrue("Test experiment failed to load: " + experiment.message, experiment.loaded);

        DataBuffer big = experiment.getBuffer("big");
        for (int i = 0; i < BIG; i++)
            big.append(i * 0.25);
        DataBuffer small = experiment.getBuffer("small");
        DataBuffer ref = experiment.getBuffer("ref");
        for (int i = 0; i < 5; i++) {
            small.append(10.0 + i);
            ref.append(i);
        }

        server = new RemoteServer(experiment, activity);
        assertTrue("RemoteServer did not start", server.start());
        base = "http://127.0.0.1:" + RemoteServer.httpServerPort;
    }

    @After
    public void stopServer() {
        server.stop();
    }

    private String get(String query) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + "/get?" + query).openConnection();
        connection.setRequestProperty("Connection", "close"); //a pooled connection would outlive this test's server
        assertEquals(200, connection.getResponseCode());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (InputStream in = connection.getInputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1)
                os.write(chunk, 0, n);
        }
        connection.disconnect();
        return os.toString("UTF-8");
    }

    private JSONObject bufferOf(String body, String name) throws Exception {
        return new JSONObject(body).getJSONObject("buffer").getJSONObject(name);
    }

    @Test
    public void theUpdateModesAnswerWhatTheyPromise() throws Exception {
        String body = get("small=full&big&ref=0");

        JSONObject full = bufferOf(body, "small");
        assertEquals("full", full.getString("updateMode"));
        assertEquals(8, full.getInt("size"));
        JSONArray values = full.getJSONArray("buffer");
        assertEquals(5, values.length());
        assertEquals(10.0, values.getDouble(0), 0.0);
        assertEquals(14.0, values.getDouble(4), 0.0);

        JSONObject single = bufferOf(body, "big");
        assertEquals("single", single.getString("updateMode"));
        assertEquals(1, single.getJSONArray("buffer").length());
        assertEquals((BIG - 1) * 0.25, single.getJSONArray("buffer").getDouble(0), 1e-6);

        JSONObject partial = bufferOf(body, "ref");
        assertEquals("partial", partial.getString("updateMode"));
        assertEquals(4, partial.getJSONArray("buffer").length());
        assertEquals(1.0, partial.getJSONArray("buffer").getDouble(0), 0.0);
    }

    @Test
    public void aThresholdCanBeMeasuredAgainstAnotherBuffer() throws Exception {
        //small=2|ref: the values of small where ref is above 2
        JSONObject filtered = bufferOf(get("small=2%7Cref"), "small");
        assertEquals("partial", filtered.getString("updateMode"));
        JSONArray values = filtered.getJSONArray("buffer");
        assertEquals(2, values.length());
        assertEquals(13.0, values.getDouble(0), 0.0);
        assertEquals(14.0, values.getDouble(1), 0.0);
    }

    @Test
    public void theDataLockIsFreeWhileTheAnswerIsWritten() throws Exception {
        final AtomicReference<Exception> failure = new AtomicReference<>();
        final AtomicReference<String> answer = new AtomicReference<>();

        long start = System.nanoTime();
        Thread request = new Thread(() -> {
            try {
                answer.set(get("big=full"));
            } catch (Exception e) {
                failure.set(e);
            }
        });
        request.start();

        //take the lock repeatedly, as the analysis does
        long worstWaitMs = 0;
        int acquisitions = 0;
        while (request.isAlive()) {
            long before = System.nanoTime();
            if (experiment.dataLock.tryLock(5, TimeUnit.SECONDS)) {
                worstWaitMs = Math.max(worstWaitMs, (System.nanoTime() - before) / 1000000);
                acquisitions++;
                experiment.dataLock.unlock();
            } else {
                worstWaitMs = 5000;
                break;
            }
            Thread.sleep(1);
        }
        request.join(10000);
        long requestMs = (System.nanoTime() - start) / 1000000;

        if (failure.get() != null)
            throw failure.get();
        assertEquals(BIG, bufferOf(answer.get(), "big").getJSONArray("buffer").length());

        assertTrue("the response was too quick to measure anything (" + requestMs + " ms) - raise BIG",
                requestMs > 100);

        assertTrue("a reader got the data lock only " + acquisitions + " times during a "
                + requestMs + " ms response - it is being held through the formatting", acquisitions > 10);
        assertTrue("the data lock was held for " + worstWaitMs + " ms of a " + requestMs
                        + " ms response - the answer is being formatted under the lock, which "
                        + "stalls the analysis and every other reader for the whole request",
                worstWaitMs < requestMs / 4);
    }
}
