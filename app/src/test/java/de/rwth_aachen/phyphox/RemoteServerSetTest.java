package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

//The /set endpoint (bulk JSON buffer write, remote API 1.1.0) against a real RemoteServer
//instance served over HTTP, mirroring the set.* probes of phyphox-docs'
//tools/contract_test.py plus assertions on the written buffer contents, which the contract
//test cannot see. This is not a test-matrix row (those only cover the file format so far),
//just a regression test for RemoteServer.handleSet.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RemoteServerSetTest {

    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Set endpoint test</title>"
                    + "<category>Test</category>"
                    + "<description>Minimal experiment for the /set endpoint test.</description>"
                    + "<data-containers>"
                    + "<container size=\"3\">abc</container>"
                    + "<container size=\"0\">unbounded</container>"
                    + "</data-containers>"
                    + "<views><view label=\"View\"><value label=\"v\"><input>abc</input></value></view></views>"
                    + "</phyphox>";

    private PhyphoxExperiment experiment;
    private RemoteServer server;
    private String base;

    @Before
    public void startServer() {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        experiment = CorpusTestEnvironment.load(new ByteArrayInputStream(EXPERIMENT.getBytes(StandardCharsets.UTF_8)), activity);
        assertTrue("Test experiment failed to load: " + experiment.message, experiment.loaded);
        server = new RemoteServer(experiment, activity);
        assertTrue("RemoteServer did not start", server.start());
        base = "http://127.0.0.1:" + RemoteServer.httpServerPort;
    }

    @After
    public void stopServer() {
        server.stop();
    }

    private static class HttpResult {
        int status;
        String body;
    }

    private HttpResult request(String method, String contentType, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + "/set").openConnection();
        connection.setRequestMethod(method);
        //No keep-alive: jlhttp's stop() closes the listening socket but lets in-flight
        //connection threads finish, and the JVM pools connections per host:port - a pooled
        //connection would let a later test talk to an earlier test's server instance.
        connection.setRequestProperty("Connection", "close");
        if (body != null) {
            connection.setRequestProperty("Content-Type", contentType);
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        HttpResult result = new HttpResult();
        result.status = connection.getResponseCode();
        InputStream in = result.status < 400 ? connection.getInputStream() : connection.getErrorStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1)
            os.write(buffer, 0, n);
        in.close();
        connection.disconnect();
        result.body = os.toString("UTF-8");
        return result;
    }

    private HttpResult post(String json) throws Exception {
        return request("POST", "application/json", json);
    }

    //A 200 response whose body carries {"result": ..., "error": ...}; error only when false.
    private boolean resultOf(HttpResult r) throws Exception {
        assertEquals("Expected a 200 result object, got " + r.status + ": " + r.body, 200, r.status);
        JSONObject json = new JSONObject(r.body);
        boolean result = json.getBoolean("result");
        if (!result)
            assertTrue("A rejection must carry an error message: " + r.body, json.getString("error").length() > 0);
        return result;
    }

    private void assertBuffer(String name, double... expected) {
        //The server writes on its executor thread under dataLock; reading under the same lock
        //establishes the happens-before edge that makes the write visible here.
        experiment.dataLock.lock();
        try {
            DataBuffer db = experiment.getBuffer(name);
            assertEquals(name + " filled size", expected.length, db.getFilledSize());
            Double[] actual = db.getArray();
            for (int i = 0; i < expected.length; i++)
                assertEquals(name + "[" + i + "]", expected[i], actual[i], 0.0);
        } finally {
            experiment.dataLock.unlock();
        }
    }

    @Test
    public void replaceWritesInOrder() throws Exception {
        assertTrue(resultOf(post("{\"buffers\": {\"abc\": [1, 2.5, 3]}}")));
        assertBuffer("abc", 1, 2.5, 3);
        assertTrue("A successful write must mark the analysis as having new data", experiment.newData);

        //replace is the default mode and clears first
        assertTrue(resultOf(post("{\"buffers\": {\"abc\": [7]}, \"mode\": \"replace\"}")));
        assertBuffer("abc", 7);
    }

    @Test
    public void specialValuesViaNullAndStrings() throws Exception {
        assertTrue(resultOf(post("{\"buffers\": {\"unbounded\": [null, \"nan\", \"Infinity\", \"-infinity\"]}}")));
        assertBuffer("unbounded", Double.NaN, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void appendKeepsExistingContent() throws Exception {
        assertTrue(resultOf(post("{\"buffers\": {\"abc\": [1, 2]}}")));
        assertTrue(resultOf(post("{\"buffers\": {\"abc\": [4]}, \"mode\": \"append\"}")));
        assertBuffer("abc", 1, 2, 4);
    }

    @Test
    public void bufferSizeSemanticsApply() throws Exception {
        //abc has size 3: the buffer keeps its newest three values
        assertTrue(resultOf(post("{\"buffers\": {\"abc\": [1, 2, 3, 4, 5]}}")));
        assertBuffer("abc", 3, 4, 5);
    }

    @Test
    public void multipleBuffersInOneRequest() throws Exception {
        assertTrue(resultOf(post("{\"buffers\": {\"abc\": [1], \"unbounded\": [2, 3]}}")));
        assertBuffer("abc", 1);
        assertBuffer("unbounded", 2, 3);
    }

    @Test
    public void emptyBuffersObjectIsANoOp() throws Exception {
        assertTrue(resultOf(post("{\"buffers\": {\"abc\": [1]}}")));
        assertTrue(resultOf(post("{\"buffers\": {}}")));
        assertBuffer("abc", 1); //untouched
    }

    @Test
    public void unknownBufferRejectsAtomically() throws Exception {
        assertTrue(resultOf(post("{\"buffers\": {\"abc\": [9]}}")));
        assertFalse(resultOf(post("{\"buffers\": {\"abc\": [1], \"nosuchbuffer___\": [1]}}")));
        assertBuffer("abc", 9); //the known buffer must not have been written
    }

    @Test
    public void invalidEntriesReject() throws Exception {
        assertFalse(resultOf(post("{\"buffers\": {\"abc\": [\"inf\"]}}"))); //outside the number lexical space
        assertFalse(resultOf(post("{\"buffers\": {\"abc\": [true]}}"))); //booleans are invalid
        assertFalse(resultOf(post("{\"buffers\": {\"abc\": [[1]]}}"))); //so are nested structures
        assertFalse(resultOf(post("{\"buffers\": {\"abc\": 1}}"))); //values must be an array
        assertFalse(resultOf(post("{\"nobuffers\": {}}"))); //the buffers object is required
    }

    @Test
    public void invalidModeRejects() throws Exception {
        assertFalse(resultOf(post("{\"buffers\": {\"abc\": [1]}, \"mode\": \"sideways\"}")));
        assertBuffer("abc"); //nothing written
    }

    @Test
    public void nonJsonRequestsReject() throws Exception {
        assertFalse(resultOf(request("GET", null, null)));
        assertFalse(resultOf(request("POST", "application/x-www-form-urlencoded", "buffers=abc")));
    }

    @Test
    public void malformedJsonBodyIs400() throws Exception {
        HttpResult r = post("{\"buffers\": {");
        assertEquals("A body that is not parseable JSON at all must answer 400: " + r.body, 400, r.status);
    }
}
