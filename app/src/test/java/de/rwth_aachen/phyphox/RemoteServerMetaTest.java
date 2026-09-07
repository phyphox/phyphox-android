package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.hardware.camera2.CameraManager;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import de.rwth_aachen.phyphox.camera.helper.CameraHelper;

///meta against a real RemoteServer: a sensor identifier outside Metadata's vocabulary throws
//inside the handler and turns the whole response into jlhttp's HTML 500.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RemoteServerMetaTest {

    private static final String EXPERIMENT =
            "<phyphox version=\"1.20\">"
                    + "<title>Meta endpoint test</title>"
                    + "<category>Test</category>"
                    + "<description>Minimal experiment for the /meta endpoint test.</description>"
                    + "<data-containers><container size=\"1\">acc</container></data-containers>"
                    + "<input><sensor type=\"accelerometer\"><output component=\"x\">acc</output></sensor></input>"
                    + "<views><view label=\"View\"><value label=\"v\"><input>acc</input></value></view></views>"
                    + "</phyphox>";

    private RemoteServer server;
    private String base;

    @Before
    public void startServer() {
        Experiment activity = CorpusTestEnvironment.fullyEquippedActivity();
        //Robolectric's simulated camera lacks the characteristics the /meta camera report reads,
        //so run without cameras; the sensor half does not depend on it.
        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        Shadows.shadowOf(cameraManager).removeCamera("0");
        CameraHelper.updateCameraList(cameraManager);

        PhyphoxExperiment experiment = CorpusTestEnvironment.load(
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
    public void answersMetadataJson() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + "/meta").openConnection();
        connection.setRequestProperty("Connection", "close"); //a pooled connection could outlive its server
        int status = connection.getResponseCode();
        InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1)
            os.write(buffer, 0, n);
        in.close();
        connection.disconnect();
        String body = os.toString("UTF-8");

        assertEquals("/meta must answer 200, got " + body, 200, status);

        JSONObject json = new JSONObject(body);
        assertTrue("/meta must report the device model", json.has("deviceModel"));

        JSONObject sensors = json.getJSONObject("sensors");
        assertTrue("/meta must report the accelerometer", sensors.has("accelerometer"));
        assertTrue("A sensor entry must carry the sensor's name",
                sensors.getJSONObject("accelerometer").has("Name"));
        assertFalse("/meta must not report a \"custom\" sensor", sensors.has("custom"));
    }
}
