package de.rwth_aachen.phyphox.camera.analyzer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static de.rwth_aachen.phyphox.camera.analyzer.LuminanceAnalyzer.luminanceFragmentShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.buildProgram;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.checkGLError;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.deleteProgram;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboTexCoordinates;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboVertices;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.interpolatingHeightFullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.interpolatingWidthFullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.packedMeanFunctions;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.setWhiteBalanceUniforms;

import android.graphics.RectF;
import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

public class SpectroscopyAnalyzer extends AnalyzingModule {

    int nSpecDownsampleSteps = 4;
    int[] wSpecDownsampleStep = new int[nSpecDownsampleSteps];
    int[] hSpecDownsampleStep = new int[nSpecDownsampleSteps];
    int[] specDownsamplingTextures = new int[nSpecDownsampleSteps];
    int[] specDownsamplingFramebuffers = new int[nSpecDownsampleSteps];

    final static String verticalHeightReductionFragmentShader =
            "precision highp float;" +
                    "uniform sampler2D texture;" +
                    "varying vec2 texPosition1;" +
                    "varying vec2 texPosition2;" +
                    "varying vec2 texPosition3;" +
                    "varying vec2 texPosition4;" +
                    packedMeanFunctions +
                    "void main () {" +
                    "   vec2 sum = unpackMean(texture2D(texture, texPosition1));" +
                    "   if (texPosition2.y <= 1.0)" +
                    "       sum += unpackMean(texture2D(texture, texPosition2));" +
                    "   if (texPosition3.y <= 1.0)" +
                    "       sum += unpackMean(texture2D(texture, texPosition3));" +
                    "   if (texPosition4.y <= 1.0)" +
                    "       sum += unpackMean(texture2D(texture, texPosition4));" +
                    "   gl_FragColor = packMean(sum / 4.0);" +
                    "}";

    final static String verticalWidthReductionFragmentShader =
            "precision highp float;" +
                    "uniform sampler2D texture;" +
                    "varying vec2 texPosition1;" +
                    "varying vec2 texPosition2;" +
                    "varying vec2 texPosition3;" +
                    "varying vec2 texPosition4;" +
                    packedMeanFunctions +
                    "void main () {" +
                    "   vec2 sum = unpackMean(texture2D(texture, texPosition1));" +
                    "   if (texPosition2.x <= 1.0)" +
                    "       sum += unpackMean(texture2D(texture, texPosition2));" +
                    "   if (texPosition3.x <= 1.0)" +
                    "       sum += unpackMean(texture2D(texture, texPosition3));" +
                    "   if (texPosition4.x <= 1.0)" +
                    "       sum += unpackMean(texture2D(texture, texPosition4));" +
                    "   gl_FragColor = packMean(sum / 4.0);" +
                    "}";



    DataBuffer pixelPosition;
    //One reduction pass per mapped spectrum: luminance and the three linear colour channels. Each
    //pass shares the ROI and orientation, so a single pixelPosition array pairs with all of them.
    LuminanceAnalyzer.Channel[] channels;
    DataBuffer[] channelOutputs;

    int spectroscopyProgram = -1;
    int verticalReductionProgram = -1;
    int spectroscopyProgramVerticesHandle, spectroscopyProgramTexCoordinatesHandle;
    int spectroscopyProgramCamMatrixHandle, spectroscopyProgramTextureHandle;
    int spectroscopyProgramPassepartoutMinHandle, spectroscopyProgramPassepartoutMaxHandle, spectroscopyProgramWeightsHandle;
    int spectroscopyProgramWhiteBalanceHandle, spectroscopyProgramWhiteBalanceMatrixHandle;

    int reductionProgramVerticesHandle, reductionProgramTexCoordinatesHandle;
    int reductionProgramTextureHandle, reductionResSourceHandle, reductionResTargetHandle;

    static class Result {
        double[][] spectra; //Indexed like channels, each as long as pixelPosition
        double[] pixelPosition;
    }
    Result latestResult = null;
    int outputWidth = 0;

    ByteBuffer resultBuffer = null;
    int resultBufferSize = 0;

    public enum SpectrumOrientation {
        PORTRAIT,
        LANDSCAPE
    }
    private SpectrumOrientation analysisSpectrumOrientation;

    public SpectroscopyAnalyzer(DataBuffer out, DataBuffer pixelPosition, DataBuffer linearRed, DataBuffer linearGreen, DataBuffer linearBlue, SpectrumOrientation analysisSpectrumOrientation){
        super();
        this.pixelPosition = pixelPosition;
        this.analysisSpectrumOrientation = analysisSpectrumOrientation;

        List<LuminanceAnalyzer.Channel> mappedChannels = new ArrayList<>();
        List<DataBuffer> mappedOutputs = new ArrayList<>();
        LuminanceAnalyzer.Channel[] all = {LuminanceAnalyzer.Channel.luma, LuminanceAnalyzer.Channel.red, LuminanceAnalyzer.Channel.green, LuminanceAnalyzer.Channel.blue};
        DataBuffer[] outputs = {out, linearRed, linearGreen, linearBlue};
        for (int i = 0; i < all.length; i++) {
            if (outputs[i] == null)
                continue;
            mappedChannels.add(all[i]);
            mappedOutputs.add(outputs[i]);
        }
        if (mappedChannels.isEmpty()) { //Only pixelPosition mapped: one pass is still needed to find the covered range
            mappedChannels.add(LuminanceAnalyzer.Channel.luma);
            mappedOutputs.add(null);
        }
        channels = mappedChannels.toArray(new LuminanceAnalyzer.Channel[0]);
        channelOutputs = mappedOutputs.toArray(new DataBuffer[0]);
    }

    public void setAnalysisSpectrumOrientation(SpectrumOrientation analysisSpectrumOrientation) {
        this.analysisSpectrumOrientation = analysisSpectrumOrientation;
    }

    @Override
    public void prepare() {

        if (specDownsamplingFramebuffers[0] != 0) {
            GLES20.glDeleteFramebuffers(nSpecDownsampleSteps, specDownsamplingFramebuffers, 0);
            GLES20.glDeleteTextures(nSpecDownsampleSteps, specDownsamplingTextures, 0);
        }

        for (int i = 0; i < nSpecDownsampleSteps; i++) {
            if (analysisSpectrumOrientation == SpectrumOrientation.LANDSCAPE) {
                hSpecDownsampleStep[i] = height; // Keep height, shrink width
                int prevW = (i == 0) ? width : wSpecDownsampleStep[i - 1];
                wSpecDownsampleStep[i] = (prevW + 3) / 4;
            } else {
                wSpecDownsampleStep[i] = width; // Keep width, shrink height
                int prevH = (i == 0) ? height : hSpecDownsampleStep[i - 1];
                hSpecDownsampleStep[i] = (prevH + 3) / 4;
            }

            specDownsamplingTextures[i] = createRenderTexture(wSpecDownsampleStep[i], hSpecDownsampleStep[i]);
            specDownsamplingFramebuffers[i] = createFramebuffer(specDownsamplingTextures[i]);
        }

        // Prepare spectroscopy conversion program
        if (spectroscopyProgram >= 0)
            deleteProgram(spectroscopyProgram);

        spectroscopyProgram = buildProgram(fullScreenVertexShader, luminanceFragmentShader);
        spectroscopyProgramVerticesHandle = GLES20.glGetAttribLocation(spectroscopyProgram, "vertices");
        spectroscopyProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(spectroscopyProgram, "texCoordinates");
        spectroscopyProgramCamMatrixHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "camMatrix");
        spectroscopyProgramTextureHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "texture");
        spectroscopyProgramPassepartoutMinHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "passepartoutMin");
        spectroscopyProgramPassepartoutMaxHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "passepartoutMax");
        spectroscopyProgramWeightsHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "weights");
        spectroscopyProgramWhiteBalanceHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "whiteBalance");
        spectroscopyProgramWhiteBalanceMatrixHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "whiteBalanceMatrix");

        if (verticalReductionProgram >= 0)
            deleteProgram(verticalReductionProgram);
        if(analysisSpectrumOrientation == SpectrumOrientation.LANDSCAPE){
            verticalReductionProgram = buildProgram(interpolatingWidthFullScreenVertexShader, verticalWidthReductionFragmentShader);
        } else {
            verticalReductionProgram = buildProgram(interpolatingHeightFullScreenVertexShader, verticalHeightReductionFragmentShader);
        }

        reductionProgramVerticesHandle = GLES20.glGetAttribLocation(verticalReductionProgram, "vertices");
        reductionProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(verticalReductionProgram, "texCoordinates");
        reductionProgramTextureHandle = GLES20.glGetUniformLocation(verticalReductionProgram, "texture");
        reductionResSourceHandle = GLES20.glGetUniformLocation(verticalReductionProgram, "resSource");
        reductionResTargetHandle = GLES20.glGetUniformLocation(verticalReductionProgram, "resTarget");

        checkGLError("SpectroscopyAnalyzer: prepare");
    }

    @Override
    public void analyze(float[] camMatrix, RectF passepartout) {
        int outW = wSpecDownsampleStep[nSpecDownsampleSteps-1];
        int outH = hSpecDownsampleStep[nSpecDownsampleSteps-1];

        if (resultBuffer == null || resultBufferSize != outW * outH) {
            resultBufferSize = outW * outH;
            resultBuffer = ByteBuffer.allocateDirect(resultBufferSize * 4).order(ByteOrder.nativeOrder());
        }

        final boolean isLandscape = (analysisSpectrumOrientation == SpectrumOrientation.LANDSCAPE);
        final int spectrumPixels = isLandscape ? outH : outW;

        Result result = new Result();
        result.spectra = new double[channels.length][spectrumPixels];
        result.pixelPosition = new double[spectrumPixels];

        //The coverage only depends on the ROI - identical for every pass
        long[] coverage = new long[spectrumPixels];

        for (int c = 0; c < channels.length; c++) {
            drawLuminance(camMatrix, passepartout, channels[c].weights);

            for(int i = 0; i < nSpecDownsampleSteps; i++){
                drawVerticalReduction(i, camMatrix);
            }

            resultBuffer.rewind();
            GLES20.glReadPixels(0, 0, outW, outH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, resultBuffer);

            resultBuffer.rewind();
            byte[] bytes = new byte[resultBuffer.remaining()];
            resultBuffer.get(bytes);

            double[] spectrum = result.spectra[c];
            for (int pixelIndex = 0; pixelIndex < bytes.length / 4 ; pixelIndex++) {
                int spectrumPixel = isLandscape ? (pixelIndex / outW) : (pixelIndex % outW);

                int byteIndex = pixelIndex * 4;
                int r = bytes[byteIndex] & 0xff;
                int g = bytes[byteIndex+1] & 0xff;
                int b = bytes[byteIndex+2] & 0xff;
                int a = bytes[byteIndex+3] & 0xff;

                result.pixelPosition[spectrumPixel] = spectrumPixel;
                spectrum[spectrumPixel] += r * 255 + g;
                if (c == 0)
                    coverage[spectrumPixel] += b * 255 + a;
            }
        }

        int minContribution = -1;
        int maxContribution = spectrumPixels-1;
        for (int i = 0; i < spectrumPixels; i++) {
            if (coverage[i] == 0)
                continue;
            if (minContribution < 0)
                minContribution = i;
            maxContribution = i;
            for (double[] spectrum : result.spectra)
                spectrum[i] /= coverage[i];
        }

        if (minContribution < 0) {
            // no pixel contributed to any column (dark or clipped frame)
            result.pixelPosition = new double[0];
            for (int c = 0; c < channels.length; c++)
                result.spectra[c] = new double[0];
        } else {
            result.pixelPosition = Arrays.copyOfRange(result.pixelPosition, minContribution, maxContribution + 1);
            for (int c = 0; c < channels.length; c++)
                result.spectra[c] = Arrays.copyOfRange(result.spectra[c], minContribution, maxContribution + 1);
        }

        checkGLError("spectroscopy analyze");

        latestResult = result;

    }

    @Override
    public void writeToBuffers(CameraSettingState state) {
        double exposureFactor = Math.pow(2.0, state.getCurrentApertureValue())/2.0 * 100.0/state.getCurrentIsoValue() *
                        (1.0e9/60.0) / state.getCurrentShutterValue();

        if (pixelPosition != null)
            pixelPosition.clear(false);
        for (DataBuffer out : channelOutputs)
            if (out != null)
                out.clear(false);

        if (latestResult != null) {
            for (int i = 0; i < latestResult.pixelPosition.length; i++) {
                if (pixelPosition != null)
                    pixelPosition.append(latestResult.pixelPosition[i]);
                for (int c = 0; c < channelOutputs.length; c++)
                    if (channelOutputs[c] != null)
                        channelOutputs[c].append(latestResult.spectra[c][i] * exposureFactor);
            }
        }

        latestResult = null;
    }

    void drawLuminance(float[] camMatrix, RectF passepartout, float[] weights) {
        makeCurrent(analyzingFramebuffer, width, height);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Calculate scissor rect
        int scissorX = (int)Math.floor(width *(1.0-Math.max(passepartout.top, passepartout.bottom)));
        int scissorY = (int)Math.floor(height *(1.0-Math.max(passepartout.left, passepartout.right)));
        int scissorW = (int)Math.ceil(width *Math.abs(passepartout.height()));
        int scissorH = (int)Math.ceil(height *Math.abs(passepartout.width()));

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(scissorX, scissorY, scissorW, scissorH);

        GLES20.glUseProgram(spectroscopyProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(spectroscopyProgramVerticesHandle);
        GLES20.glVertexAttribPointer(spectroscopyProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(spectroscopyProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(spectroscopyProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glUniform1i(spectroscopyProgramTextureHandle, 0);

        GLES20.glUniform2f(spectroscopyProgramPassepartoutMinHandle, passepartout.left, passepartout.top);
        GLES20.glUniform2f(spectroscopyProgramPassepartoutMaxHandle, passepartout.right, passepartout.bottom);
        GLES20.glUniform3fv(spectroscopyProgramWeightsHandle, 1, weights, 0);
        setWhiteBalanceUniforms(spectroscopyProgramWhiteBalanceHandle, spectroscopyProgramWhiteBalanceMatrixHandle);

        GLES20.glUniformMatrix4fv(spectroscopyProgramCamMatrixHandle, 1, false, camMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glDisableVertexAttribArray(spectroscopyProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(spectroscopyProgramTexCoordinatesHandle);

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        checkGLError("draw luminance");
    }

    void drawVerticalReduction(int step, float[] camMatrix) {
        makeCurrent(specDownsamplingFramebuffers[step], wSpecDownsampleStep[step], hSpecDownsampleStep[step]);

        GLES20.glUseProgram(verticalReductionProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(reductionProgramVerticesHandle);
        GLES20.glVertexAttribPointer(reductionProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(reductionProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(reductionProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, (step == 0) ? analyzingTexture : specDownsamplingTextures[step-1]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glUniform1i(reductionProgramTextureHandle, 0);

        GLES20.glUniform2f(reductionResSourceHandle, step == 0 ? width : wSpecDownsampleStep[step-1], step == 0 ? height : hSpecDownsampleStep[step-1]);
        GLES20.glUniform2f(reductionResTargetHandle, wSpecDownsampleStep[step], hSpecDownsampleStep[step]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(reductionProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(reductionProgramTexCoordinatesHandle);

        checkGLError("vertical reduction");
    }
}
