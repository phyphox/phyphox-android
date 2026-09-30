package de.rwth_aachen.phyphox;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

// phyphox-test: camera-color-channels
//The value-level contract of the colour-channel outputs of the camera input (file format 1.21,
//phyphox-docs docs/file-format/input.md "Colour channels") on the two generated corpus fixtures: per frame
//luma = 0.2126·red + 0.7152·green + 0.0722·blue and luminance = the same combination of the linear channels,
//and under spectroscopy the linear channels hold exactly as many values as pixelPosition while red, green
//and blue keep one value per frame. Each analyzer rounds its per-pixel value to 8 bits before averaging,
//so the identities hold to a few thousandths of the full scale, scaled by the exposure factor.
@RunWith(AndroidJUnit4.class)
public class CameraColorChannelsTest {
    private static final String PHOTOMETRIC = "camera-rgb.phyphox";
    private static final String SPECTRUM = "camera-rgb-spectrum.phyphox";
    private static final int FRAMES = 10;

    private Experiment activity;

    @After
    public void closeFixture() {
        if (activity != null) {
            getInstrumentation().runOnMainSync(activity::stopMeasurement);
            FixtureExperiment.close(activity);
        }
    }

    private void startAndAwaitFrames(String fixture, String countedBuffer) throws Exception {
        assumeTrue("phyphox-docs was not checked out next to this repository at build time",
                FixtureExperiment.available(fixture));
        activity = FixtureExperiment.launch(fixture);
        getInstrumentation().runOnMainSync(activity::startMeasurement);

        long deadline = System.currentTimeMillis() + 30000;
        while (activity.experiment.getBuffer(countedBuffer).getFilledSize() < FRAMES && System.currentTimeMillis() < deadline)
            Thread.sleep(200);
        assertTrue("the camera delivered fewer than " + FRAMES + " frames in 30 s",
                activity.experiment.getBuffer(countedBuffer).getFilledSize() >= FRAMES);
        getInstrumentation().runOnMainSync(activity::stopMeasurement);
    }

    //A consistent copy of the named buffers, taken under the experiment's data lock
    private Map<String, Double[]> snapshot(String... names) {
        Map<String, Double[]> result = new HashMap<>();
        activity.experiment.dataLock.lock();
        try {
            for (String name : names)
                result.put(name, activity.experiment.getBuffer(name).getArray());
        } finally {
            activity.experiment.dataLock.unlock();
        }
        return result;
    }

    @Test
    public void channelsDecomposeLumaAndLuminance() throws Exception {
        startAndAwaitFrames(PHOTOMETRIC, "luma");
        Map<String, Double[]> data = snapshot("luma", "luminance", "red", "green", "blue",
                "linearRed", "linearGreen", "linearBlue", "shutterSpeed", "iso", "aperture");

        int n = data.get("luma").length;
        for (String name : data.keySet())
            assertEquals(name + " is written in the same step as luma", n, data.get(name).length);

        boolean sawLight = false;
        for (int i = 0; i < n; i++) {
            double red = data.get("red")[i], green = data.get("green")[i], blue = data.get("blue")[i];
            for (double channel : new double[]{red, green, blue})
                assertTrue("gamma channel outside 0..1 at frame " + i + ": " + channel, channel >= 0.0 && channel <= 1.0);
            double luma = data.get("luma")[i];
            double combined = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
            assertEquals("luma at frame " + i, combined, luma, 0.005);
            if (luma > 0.01)
                sawLight = true;

            double exposureFactor = Math.pow(2.0, data.get("aperture")[i]) / 2.0 * 100.0 / data.get("iso")[i]
                    * (1.0 / 60.0) / data.get("shutterSpeed")[i];
            double luminance = data.get("luminance")[i];
            double linear = 0.2126 * data.get("linearRed")[i] + 0.7152 * data.get("linearGreen")[i] + 0.0722 * data.get("linearBlue")[i];
            assertEquals("luminance at frame " + i, linear, luminance, 0.005 * exposureFactor + 0.01 * Math.abs(luminance));
        }
        assertTrue("every frame was black, the identities were only tested on zeros", sawLight);
    }

    @Test
    public void linearChannelsFormSpectraOfTheSameLength() throws Exception {
        startAndAwaitFrames(SPECTRUM, "t");
        Map<String, Double[]> data = snapshot("t", "pixelPosition", "luminance",
                "linearRed", "linearGreen", "linearBlue", "red", "green", "blue");

        int frames = data.get("t").length;
        for (String scalar : new String[]{"red", "green", "blue"})
            assertEquals(scalar + " keeps one value per frame", frames, data.get(scalar).length);

        int pixels = data.get("pixelPosition").length;
        assertTrue("the spectrum is empty", pixels > 0);
        for (String spectrum : new String[]{"luminance", "linearRed", "linearGreen", "linearBlue"})
            assertEquals(spectrum + " has as many values as pixelPosition", pixels, data.get(spectrum).length);

        //The spectrum fixture does not map the exposure outputs, so the factor comes from the camera state;
        //auto exposure may have stepped it since the last frame, hence the looser bound
        de.rwth_aachen.phyphox.camera.model.CameraSettingState state = activity.experiment.cameraInput.getCameraSettingState().getValue();
        double exposureFactor = Math.pow(2.0, state.getCurrentApertureValue()) / 2.0 * 100.0 / state.getCurrentIsoValue()
                * (1.0e9 / 60.0) / state.getCurrentShutterValue();
        for (int i = 0; i < pixels; i++) {
            double linear = 0.2126 * data.get("linearRed")[i] + 0.7152 * data.get("linearGreen")[i] + 0.0722 * data.get("linearBlue")[i];
            assertEquals("luminance at pixel " + i, linear, data.get("luminance")[i], 0.01 * exposureFactor + 0.02 * Math.abs(data.get("luminance")[i]));
        }
    }
}
