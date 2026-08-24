package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

//An error response of this API is never empty: whatever the status code, it carries a JSON
//error object (the same rule phyphox-docs' contract test checks API-wide). That has to hold for
//an unexpected failure inside a handler as well, which used to escape into jlhttp's HTML error
//page - and without the CORS header, so a browser client could not even read the status.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RemoteServerErrorResponseTest {

    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Error response test</title>"
                    + "<category>Test</category>"
                    + "<description>Minimal experiment for the error response test.</description>"
                    + "<data-containers><container size=\"1\">x</container></data-containers>"
                    + "<views><view label=\"View\"><value label=\"v\"><input>x</input></value></view></views>"
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
        server = new RemoteServer(experiment, activity);
        assertTrue("RemoteServer did not start", server.start());
        base = "http://127.0.0.1:" + RemoteServer.httpServerPort;
    }

    @After
    public void stopServer() {
        server.stop();
    }

    @Test
    public void unexpectedFailureAnswersJsonError() throws Exception {
        //Break the experiment behind the server's back. /time dereferences the time reference
        //directly, so this stands in for any unexpected failure inside a handler - the point is
        //the shape of the answer, not this particular fault.
        experiment.experimentTimeReference = null;

        HttpURLConnection connection = (HttpURLConnection) new URL(base + "/time").openConnection();
        connection.setRequestProperty("Connection", "close");
        int status = connection.getResponseCode();
        String contentType = connection.getContentType();
        InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1)
            os.write(buffer, 0, n);
        in.close();
        String cors = connection.getHeaderField("Access-Control-Allow-Origin");
        connection.disconnect();
        String body = os.toString("UTF-8");

        assertEquals("A failing handler must answer 500, got " + body, 500, status);
        assertTrue("An error response is served as JSON, not as an HTML page: " + contentType,
                contentType != null && contentType.startsWith("application/json"));
        assertTrue("An error response carries a human-readable reason: " + body,
                new JSONObject(body).getString("error").length() > 0);
        assertEquals("An error response keeps the CORS header the API sets everywhere", "*", cors);
    }
}
