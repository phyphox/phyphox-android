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

//The /meta endpoint against a real RemoteServer instance. It answers device and per-sensor
//metadata, and asking Metadata for an identifier outside its vocabulary throws - which the
//handler does not catch, so a single bad identifier turns the whole response into jlhttp's
//HTML 500. That is what happened when "custom" left the vocabulary while the handler still
//iterated every SensorName. Not a test-matrix row; a regression test for RemoteServer.handleMeta.
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
        //The camera capability report in /meta reads characteristics that Robolectric's
        //simulated camera cannot provide (the framework asserts on the vendor key lists), so
        //this test runs against a device without cameras - the report is then simply empty.
        //The sensor half of /meta, which is what broke, does not depend on it.
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
        //No keep-alive, see RemoteServerSetTest: a pooled connection could outlive its server.
        connection.setRequestProperty("Connection", "close");
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
        //"custom" is selected by nameFilter, so per-sensor metadata for it does not exist.
        assertFalse("/meta must not report a \"custom\" sensor", sensors.has("custom"));
    }
}
