package de.rwth_aachen.phyphox.camera.analyzer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import de.rwth_aachen.phyphox.CorpusTestEnvironment;
import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.camera.CameraInput;

//The colour-channel outputs of the camera input (file format 1.21, phyphox-docs docs/file-format/input.md
//"Colour channels"): the parser wires the six components to the right buffers by position, and the renderer
//registers one GPU pass per mapped output - gamma channels always, linear channels as scalars under photometry
//and as spectra next to luminance under spectroscopy. The values themselves need a camera (T1 row
//camera-color-channels).
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CameraColorChannelWiringTest {

    private static CameraInput load(String fixture) throws Exception {
        return CorpusTestEnvironment.loadGeneratedFixture(fixture).cameraInput;
    }

    private static AnalyzingOpenGLRenderer renderer(CameraInput cameraInput) {
        return new AnalyzingOpenGLRenderer(cameraInput, cameraInput.getDataLock(), cameraInput.getCameraSettingState(), null);
    }

    private static List<LuminanceAnalyzer> luminanceAnalyzers(AnalyzingOpenGLRenderer renderer) {
        List<LuminanceAnalyzer> result = new ArrayList<>();
        for (AnalyzingModule module : renderer.analyzingModules)
            if (module instanceof LuminanceAnalyzer)
                result.add((LuminanceAnalyzer) module);
        return result;
    }

    private static SpectroscopyAnalyzer spectroscopyAnalyzer(AnalyzingOpenGLRenderer renderer) {
        SpectroscopyAnalyzer result = null;
        for (AnalyzingModule module : renderer.analyzingModules)
            if (module instanceof SpectroscopyAnalyzer) {
                assertNull("more than one spectroscopy analyzer registered", result);
                result = (SpectroscopyAnalyzer) module;
            }
        return result;
    }

    private static void assertRegistered(List<LuminanceAnalyzer> analyzers, DataBuffer out, boolean linear, LuminanceAnalyzer.Channel channel) {
        for (LuminanceAnalyzer analyzer : analyzers)
            if (analyzer.out == out) {
                assertEquals(out.name + " linear", linear, analyzer.linear);
                assertEquals(out.name + " channel", channel, analyzer.channel);
                return;
            }
        throw new AssertionError("no analyzer registered for " + out.name);
    }

    @Test
    public void photometricFixtureWiresEveryComponent() throws Exception {
        CameraInput cameraInput = load("camera-rgb.phyphox");

        assertEquals("red", cameraInput.getDataRed().name);
        assertEquals("green", cameraInput.getDataGreen().name);
        assertEquals("blue", cameraInput.getDataBlue().name);
        assertEquals("linearRed", cameraInput.getDataLinearRed().name);
        assertEquals("linearGreen", cameraInput.getDataLinearGreen().name);
        assertEquals("linearBlue", cameraInput.getDataLinearBlue().name);
        //The neighbours the new positions must not have shifted
        assertEquals("luma", cameraInput.getDataLuma().name);
        assertEquals("luminance", cameraInput.getDataLuminance().name);
        assertEquals("shutterSpeed", cameraInput.getShutterSpeedDataBuffer().name);
        assertEquals("iso", cameraInput.getIsoDataBuffer().name);
        assertEquals("aperture", cameraInput.getApertureDataBuffer().name);
        assertNull(cameraInput.getDataPixelPosition());

        AnalyzingOpenGLRenderer renderer = renderer(cameraInput);
        List<LuminanceAnalyzer> analyzers = luminanceAnalyzers(renderer);
        assertEquals("luma, luminance and the six channels: one pass each", 8, analyzers.size());
        assertRegistered(analyzers, cameraInput.getDataLuma(), false, LuminanceAnalyzer.Channel.luma);
        assertRegistered(analyzers, cameraInput.getDataLuminance(), true, LuminanceAnalyzer.Channel.luma);
        assertRegistered(analyzers, cameraInput.getDataRed(), false, LuminanceAnalyzer.Channel.red);
        assertRegistered(analyzers, cameraInput.getDataGreen(), false, LuminanceAnalyzer.Channel.green);
        assertRegistered(analyzers, cameraInput.getDataBlue(), false, LuminanceAnalyzer.Channel.blue);
        assertRegistered(analyzers, cameraInput.getDataLinearRed(), true, LuminanceAnalyzer.Channel.red);
        assertRegistered(analyzers, cameraInput.getDataLinearGreen(), true, LuminanceAnalyzer.Channel.green);
        assertRegistered(analyzers, cameraInput.getDataLinearBlue(), true, LuminanceAnalyzer.Channel.blue);
        assertNull("no spectroscopy pass under the photometric feature", spectroscopyAnalyzer(renderer));
    }

    @Test
    public void spectroscopyFixtureRunsTheLinearChannelsAsSpectra() throws Exception {
        CameraInput cameraInput = load("camera-rgb-spectrum.phyphox");

        assertEquals("pixelPosition", cameraInput.getDataPixelPosition().name);
        assertEquals("linearRed", cameraInput.getDataLinearRed().name);

        AnalyzingOpenGLRenderer renderer = renderer(cameraInput);

        //red, green and blue stay per-frame scalars; the linear channels do not get a scalar pass here
        List<LuminanceAnalyzer> analyzers = luminanceAnalyzers(renderer);
        assertEquals(3, analyzers.size());
        assertRegistered(analyzers, cameraInput.getDataRed(), false, LuminanceAnalyzer.Channel.red);
        assertRegistered(analyzers, cameraInput.getDataGreen(), false, LuminanceAnalyzer.Channel.green);
        assertRegistered(analyzers, cameraInput.getDataBlue(), false, LuminanceAnalyzer.Channel.blue);

        SpectroscopyAnalyzer spectroscopy = spectroscopyAnalyzer(renderer);
        assertSame(cameraInput.getDataPixelPosition(), spectroscopy.pixelPosition);
        assertEquals(4, spectroscopy.channels.length);
        assertEquals(LuminanceAnalyzer.Channel.luma, spectroscopy.channels[0]);
        assertSame(cameraInput.getDataLuminance(), spectroscopy.channelOutputs[0]);
        assertEquals(LuminanceAnalyzer.Channel.red, spectroscopy.channels[1]);
        assertSame(cameraInput.getDataLinearRed(), spectroscopy.channelOutputs[1]);
        assertEquals(LuminanceAnalyzer.Channel.green, spectroscopy.channels[2]);
        assertSame(cameraInput.getDataLinearGreen(), spectroscopy.channelOutputs[2]);
        assertEquals(LuminanceAnalyzer.Channel.blue, spectroscopy.channels[3]);
        assertSame(cameraInput.getDataLinearBlue(), spectroscopy.channelOutputs[3]);
    }

    @Test
    public void unmappedOutputsCostNoPass() throws Exception {
        CameraInput cameraInput = load("camera-rgb-spectrum.phyphox");
        DataBuffer luminance = cameraInput.getDataLuminance();
        DataBuffer pixelPosition = cameraInput.getDataPixelPosition();

        SpectroscopyAnalyzer onlyLuminance = new SpectroscopyAnalyzer(luminance, pixelPosition, null, null, null, SpectroscopyAnalyzer.SpectrumOrientation.LANDSCAPE);
        assertEquals(1, onlyLuminance.channels.length);
        assertSame(luminance, onlyLuminance.channelOutputs[0]);

        //pixelPosition alone still needs one reduction to find the covered range
        SpectroscopyAnalyzer onlyPositions = new SpectroscopyAnalyzer(null, pixelPosition, null, null, null, SpectroscopyAnalyzer.SpectrumOrientation.LANDSCAPE);
        assertEquals(1, onlyPositions.channels.length);
        assertNull(onlyPositions.channelOutputs[0]);
    }
}
