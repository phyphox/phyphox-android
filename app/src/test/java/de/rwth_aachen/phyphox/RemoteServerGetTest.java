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

//The /get endpoint against a real RemoteServer, on two counts.
//
//What it answers: the three update modes, the threshold filter and the reference-buffer form
//(y=<threshold>|x), which is the part with the most room to go wrong.
//
//And how long it holds the data lock while answering. Everything that reads buffer data takes
//that lock, and the analysis takes it several times per pass, so a reader that keeps it for the
//whole response holds up the experiment for as long as the response takes to write - and writing
//it is the slow half, a DecimalFormat call per value. So the handler copies what it needs under
//the lock and formats afterwards, and this measures that: while a request for a large buffer is
//in flight, another thread must be able to take the lock quickly, over and over.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RemoteServerGetTest {

    //Big enough that formatting it dominates the response, which is the situation the lock must
    //not be held through.
    private static final int BIG = 250000;

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
        //No keep-alive, for the same reason RemoteServerSetTest gives: a pooled connection would
        //outlive the server instance of the test that opened it.
        connection.setRequestProperty("Connection", "close");
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

        //No value after the name at all: the last value only, and the array is never copied.
        JSONObject single = bufferOf(body, "big");
        assertEquals("single", single.getString("updateMode"));
        assertEquals(1, single.getJSONArray("buffer").length());
        assertEquals((BIG - 1) * 0.25, single.getJSONArray("buffer").getDouble(0), 1e-6);

        //A numeric threshold keeps the values above it.
        JSONObject partial = bufferOf(body, "ref");
        assertEquals("partial", partial.getString("updateMode"));
        assertEquals(4, partial.getJSONArray("buffer").length());
        assertEquals(1.0, partial.getJSONArray("buffer").getDouble(0), 0.0);
    }

    @Test
    public void aThresholdCanBeMeasuredAgainstAnotherBuffer() throws Exception {
        //small=2|ref: keep the values of small at the indices where ref is above 2.
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

        //While that runs, take the lock over and over the way the analysis does, and remember the
        //longest anyone had to wait for it.
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

        //The measurement is only worth anything if writing the answer took a while.
        assertTrue("the response was too quick to measure anything (" + requestMs + " ms) - raise BIG",
                requestMs > 100);

        //Measured both ways on this test: holding the lock through the formatting, as the handler
        //used to, gives a worst wait of 146 ms out of a 171 ms response and lets a competing
        //reader in twice; copying first gives 2 ms out of 168 ms and lets it in 122 times.
        assertTrue("a reader got the data lock only " + acquisitions + " times during a "
                + requestMs + " ms response - it is being held through the formatting", acquisitions > 10);
        assertTrue("the data lock was held for " + worstWaitMs + " ms of a " + requestMs
                        + " ms response - the answer is being formatted under the lock, which "
                        + "stalls the analysis and every other reader for the whole request",
                worstWaitMs < requestMs / 4);
    }
}
