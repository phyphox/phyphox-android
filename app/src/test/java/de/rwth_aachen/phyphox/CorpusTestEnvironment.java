package de.rwth_aachen.phyphox;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;

import androidx.test.core.app.ApplicationProvider;

import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowCameraCharacteristics;
import org.robolectric.shadows.ShadowSensor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//Shared plumbing for the corpus conformance tests (see phyphox-docs/corpus/README.md, section
//"The app test suites"): locates the conformance corpus in a phyphox-docs checkout next to this
//repository, sets up a Robolectric environment that looks like a fully equipped device (the
//parser refuses experiments whose sensors/permissions are missing, which would wrongly fail
//valid corpus files on the JVM), and runs files through the real loading path.
abstract class CorpusTestEnvironment {

    //Sentinel parameter used by the parameterized tests when no corpus checkout is present, so
    //the suite reports a visible skip instead of failing on an empty parameter list.
    static final String CORPUS_MISSING = "corpus missing";

    //The corpus sits in a phyphox-docs checkout next to this repository. The tests' working
    //directory is somewhere inside the repository (usually the app module), so walk up.
    static File findCorpus() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int i = 0; i < 8 && dir != null; i++) {
            File corpus = new File(new File(dir, "phyphox-docs"), "corpus");
            if (corpus.isDirectory())
                return corpus;
            dir = dir.getParentFile();
        }
        return null;
    }

    //All .phyphox files under dir, as paths relative to base, sorted for stable test names.
    static List<String> listPhyphoxFiles(File base, File dir) {
        List<String> result = new ArrayList<>();
        File[] entries = dir.listFiles();
        if (entries == null)
            return result;
        for (File entry : entries) {
            if (entry.isDirectory())
                result.addAll(listPhyphoxFiles(base, entry));
            else if (entry.getName().toLowerCase().endsWith(".phyphox"))
                result.add(base.toPath().relativize(entry.toPath()).toString());
        }
        result.sort(String::compareTo);
        return result;
    }

    //Minimal reader for corpus/invalid/expected.yml: per top-level "<file>.phyphox:" entry,
    //extract the "parser: rejects|accepts" classification. Deliberately not a full YAML parser -
    //the file's documented shape is flat, and this avoids a test-only dependency.
    static java.util.Map<String, String> parserClassification(File corpus) throws IOException {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        Pattern fileEntry = Pattern.compile("^([^\\s:#]+\\.phyphox):");
        Pattern parserEntry = Pattern.compile("^\\s+parser:\\s*(rejects|accepts)\\b");
        String currentFile = null;
        for (String line : Files.readAllLines(new File(corpus, "invalid/expected.yml").toPath(), StandardCharsets.UTF_8)) {
            Matcher m = fileEntry.matcher(line);
            if (m.find()) {
                currentFile = m.group(1);
                continue;
            }
            m = parserEntry.matcher(line);
            if (m.find() && currentFile != null)
                result.put(currentFile, m.group(1));
        }
        return result;
    }

    //The version attribute of the root phyphox element as {major, minor}, or null if the file
    //does not declare one (which the parser allows).
    static int[] declaredVersion(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<phyphox\\b[^>]*\\bversion\\s*=\\s*\"(\\d+)\\.(\\d+)\"",
                Pattern.CASE_INSENSITIVE).matcher(content);
        if (!m.find())
            return null;
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

    //PhyphoxFile.phyphoxFileVersion as {major, minor}.
    static int[] supportedVersion() {
        int split = PhyphoxFile.phyphoxFileVersion.indexOf('.');
        return new int[]{Integer.parseInt(PhyphoxFile.phyphoxFileVersion.substring(0, split)),
                Integer.parseInt(PhyphoxFile.phyphoxFileVersion.substring(split + 1))};
    }

    static boolean versionAtMostSupported(int[] version) {
        int[] supported = supportedVersion();
        return version[0] < supported[0] || (version[0] == supported[0] && version[1] <= supported[1]);
    }

    //An Experiment activity backed by a simulated device that has every sensor and capability
    //the corpus needs, so that only actual parse errors can fail a file.
    static Experiment fullyEquippedActivity() {
        Application application = ApplicationProvider.getApplicationContext();

        Shadows.shadowOf(application).grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT);

        Shadows.shadowOf(application.getPackageManager()).setSystemFeature(PackageManager.FEATURE_CAMERA, true);
        Shadows.shadowOf(application.getPackageManager()).setSystemFeature(PackageManager.FEATURE_LOCATION_GPS, true);
        Shadows.shadowOf(application.getPackageManager()).setSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE, true);

        SensorManager sensorManager = (SensorManager) application.getSystemService(Context.SENSOR_SERVICE);
        int[] sensorTypes = {
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_LINEAR_ACCELERATION,
                Sensor.TYPE_GRAVITY,
                Sensor.TYPE_GYROSCOPE,
                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                Sensor.TYPE_PRESSURE,
                Sensor.TYPE_LIGHT,
                Sensor.TYPE_PROXIMITY,
                Sensor.TYPE_AMBIENT_TEMPERATURE,
                Sensor.TYPE_RELATIVE_HUMIDITY,
                Sensor.TYPE_ROTATION_VECTOR,
        };
        for (int type : sensorTypes)
            Shadows.shadowOf(sensorManager).addSensor(makeSensor(type, "simulated sensor type " + type));
        //Vendor-specific sensors for the type="custom" corpus fixtures: one matched by
        //typeFilter (rear-light-sensor.phyphox), one matched by nameFilter
        //("ICP10101 Temperature.phyphox").
        Shadows.shadowOf(sensorManager).addSensor(makeSensor(65545, "simulated rear light sensor"));
        Shadows.shadowOf(sensorManager).addSensor(makeSensor(65537, "ICP10101 Temperature"));

        //A back camera that also reports the depth capability, for the camera and depth inputs.
        CameraManager cameraManager = (CameraManager) application.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = ShadowCameraCharacteristics.newCameraCharacteristics();
        ShadowCameraCharacteristics shadowCharacteristics = Shadows.shadowOf(characteristics);
        shadowCharacteristics.set(CameraCharacteristics.LENS_FACING, CameraMetadata.LENS_FACING_BACK);
        shadowCharacteristics.set(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES, new int[]{
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE,
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT});
        Shadows.shadowOf(cameraManager).addCamera("0", characteristics);

        //The parser takes the hosting Experiment activity as its context. It only uses the
        //activity as a Context and for its sensorManager field, so an attached but not started
        //activity is sufficient - running onCreate would start loading an experiment itself.
        Experiment activity = Robolectric.buildActivity(Experiment.class).get();
        if (activity.getBaseContext() == null)
            Shadows.shadowOf(activity).callAttach(new Intent());
        activity.sensorManager = sensorManager;
        return activity;
    }

    private static Sensor makeSensor(int type, String name) {
        Sensor sensor = ShadowSensor.newInstance(type);
        try {
            Field nameField = Sensor.class.getDeclaredField("mName");
            nameField.setAccessible(true);
            nameField.set(sensor, name);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot name simulated sensor", e);
        }
        return sensor;
    }

    //Run a file through the real loading path, the way loadXMLAsyncTask does once the stream
    //is open.
    static PhyphoxExperiment load(File file, Experiment activity) throws IOException {
        try (InputStream inputStream = new FileInputStream(file)) {
            return load(inputStream, activity);
        }
    }

    static PhyphoxExperiment load(InputStream inputStream, Experiment activity) {
        PhyphoxFile.PhyphoxStream stream = new PhyphoxFile.PhyphoxStream();
        stream.inputStream = inputStream;
        stream.isLocal = true;
        return PhyphoxFile.loadExperiment(stream, activity);
    }
}
