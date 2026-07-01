package de.rwth_aachen.phyphox.camera.analyzer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static de.rwth_aachen.phyphox.camera.analyzer.LuminanceAnalyzer.lumaFragmentShader;
import static de.rwth_aachen.phyphox.camera.analyzer.LuminanceAnalyzer.luminanceFragmentShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.buildProgram;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.checkGLError;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.deleteProgram;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboTexCoordinates;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboVertices;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.interpolatingHeightFullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.interpolatingWidthFullScreenVertexShader;

import android.graphics.RectF;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

public class SpectroscopyAnalyzer extends AnalyzingModule {

    int nSpecDownsampleSteps = 4;
    EGLSurface[] specDownsampleSurfaces = new EGLSurface[nSpecDownsampleSteps];
    int[] wSpecDownsampleStep = new int[nSpecDownsampleSteps];
    int[] hSpecDownsampleStep = new int[nSpecDownsampleSteps];
    int[] specDownsamplingTextures = new int[nSpecDownsampleSteps];

    final static String verticalHeightReductionFragmentShader =
            "precision highp float;" +
                    "uniform sampler2D texture;" +
                    "varying vec2 texPosition1;" +
                    "varying vec2 texPosition2;" +
                    "varying vec2 texPosition3;" +
                    "varying vec2 texPosition4;" +
                    "void main () {" +
                    "   vec4 result = texture2D(texture, texPosition1);" +
                    "   if (texPosition2.y <= 1.0)" +
                    "       result += texture2D(texture, texPosition2);" +
                    "   if (texPosition3.y <= 1.0)" +
                    "       result += texture2D(texture, texPosition3);" +
                    "   if (texPosition4.y <= 1.0)" +
                    "       result += texture2D(texture, texPosition4);" +
                    "   float overflow = floor(result.g);" +
                    "   result.g = result.g - overflow;" +
                    "   result.r = result.r + overflow / 255.0;" +
                    "   result.b = result.b / 4.0;" +
                    "   gl_FragColor = result;" +
                    "}";

    final static String verticalWidthReductionFragmentShader =
            "precision highp float;" +
                    "uniform sampler2D texture;" +
                    "varying vec2 texPosition1;" +
                    "varying vec2 texPosition2;" +
                    "varying vec2 texPosition3;" +
                    "varying vec2 texPosition4;" +
                    "void main () {" +
                    "   vec4 result = texture2D(texture, texPosition1);" +
                    "   if (texPosition2.x <= 1.0)" +
                    "       result += texture2D(texture, texPosition2);" +
                    "   if (texPosition3.x <= 1.0)" +
                    "       result += texture2D(texture, texPosition3);" +
                    "   if (texPosition4.x <= 1.0)" +
                    "       result += texture2D(texture, texPosition4);" +
                    "   float overflow = floor(result.g);" +
                    "   result.g = result.g - overflow;" +
                    "   result.r = result.r + overflow / 255.0;" +
                    "   result.b = result.b / 4.0;" +
                    "   gl_FragColor = result;" +
                    "}";



    DataBuffer out;
    DataBuffer pixelPosition;

    int spectroscopyProgram = -1;
    int verticalReductionProgram = -1;
    int spectroscopyProgramVerticesHandle, spectroscopyProgramTexCoordinatesHandle;
    int spectroscopyProgramCamMatrixHandle, spectroscopyProgramTextureHandle;
    int spectroscopyProgramPassepartoutMinHandle, spectroscopyProgramPassepartoutMaxHandle;

    int reductionProgramVerticesHandle, reductionProgramTexCoordinatesHandle;
    int reductionProgramTextureHandle, reductionResSourceHandle, reductionResTargetHandle;

    double[] latestResult = null;
    int outputWidth = 0;

    ByteBuffer resultBuffer = null;
    int resultBufferSize = 0;

    public enum SpectrumOrientation {
        PORTRAIT,
        LANDSCAPE
    }
    private SpectrumOrientation analysisSpectrumOrientation;

    public SpectroscopyAnalyzer(DataBuffer out, DataBuffer pixelPosition, SpectrumOrientation analysisSpectrumOrientation){
        super();
        this.out = out;
        this.pixelPosition = pixelPosition;
        this.analysisSpectrumOrientation = analysisSpectrumOrientation;
    }

    public void setAnalysisSpectrumOrientation(SpectrumOrientation analysisSpectrumOrientation) {
        this.analysisSpectrumOrientation = analysisSpectrumOrientation;
    }

    @Override
    public void prepare() {

        if (specDownsampleSurfaces != null) {
            for (EGLSurface surface : specDownsampleSurfaces) {
                if (surface != null) EGL14.eglDestroySurface(eglDisplay, surface);
            }
        }
        if (specDownsamplingTextures != null) {
            GLES20.glDeleteTextures(nSpecDownsampleSteps, specDownsamplingTextures, 0);
        }

        GLES20.glGenTextures(nSpecDownsampleSteps, specDownsamplingTextures, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        for (int i = 0; i < nSpecDownsampleSteps; i++) {
            if (analysisSpectrumOrientation == SpectrumOrientation.PORTRAIT) {
                wSpecDownsampleStep[i] = width; // Keep width, shrink height
                int prevH = (i == 0) ? height : hSpecDownsampleStep[i - 1];
                hSpecDownsampleStep[i] = (prevH + 3) / 4;
            } else {
                hSpecDownsampleStep[i] = height; // Keep height, shrink width
                int prevW = (i == 0) ? width : wSpecDownsampleStep[i - 1];
                wSpecDownsampleStep[i] = (prevW + 3) / 4;
            }

            specDownsampleSurfaces[i] = AnalyzingModule.createPbufferSurface( wSpecDownsampleStep[i], hSpecDownsampleStep[i]);
        }

        // Prepare spectroscopy conversion program
        if (spectroscopyProgram == -1) {
            spectroscopyProgram = buildProgram(fullScreenVertexShader, luminanceFragmentShader);
            spectroscopyProgramVerticesHandle = GLES20.glGetAttribLocation(spectroscopyProgram, "vertices");
            spectroscopyProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(spectroscopyProgram, "texCoordinates");
            spectroscopyProgramCamMatrixHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "camMatrix");
            spectroscopyProgramTextureHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "texture");
            spectroscopyProgramPassepartoutMinHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "passepartoutMin");
            spectroscopyProgramPassepartoutMaxHandle = GLES20.glGetUniformLocation(spectroscopyProgram, "passepartoutMax");
        }

        if (verticalReductionProgram >= 0)
            deleteProgram(verticalReductionProgram);
        if(analysisSpectrumOrientation == SpectrumOrientation.PORTRAIT){
            verticalReductionProgram = buildProgram(interpolatingHeightFullScreenVertexShader, verticalHeightReductionFragmentShader);
        } else {
            verticalReductionProgram = buildProgram(interpolatingWidthFullScreenVertexShader, verticalWidthReductionFragmentShader);
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
        // --- Phase 1: OpenGL Drawing/Downsampling ---
        drawLuminance(camMatrix, passepartout);

        for(int i = 0; i < nSpecDownsampleSteps; i++){
            drawVerticalReduction(i, camMatrix);
        }

        // --- Phase 2: Setup and GL Read ---

        int outW = wSpecDownsampleStep[nSpecDownsampleSteps -1];
        int outH = hSpecDownsampleStep[nSpecDownsampleSteps -1];

        if (resultBuffer == null || resultBufferSize != outW * outH) {
            resultBufferSize = outW * outH;
            resultBuffer = ByteBuffer.allocateDirect(resultBufferSize * 4).order(ByteOrder.nativeOrder());
        }
        resultBuffer.rewind();

        // Read pixels from the OpenGL framebuffer
        GLES20.glReadPixels(0, 0, outW, outH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, resultBuffer);
        resultBuffer.rewind();

        byte[] bytes = new byte[resultBuffer.remaining()];
        resultBuffer.get(bytes);

        // --- Phase 3: Processing ---

        final boolean isHorizontal = analysisSpectrumOrientation == SpectrumOrientation.PORTRAIT;
        // Define which dimension is the dispersion (length) and which is the averaging (width)
        final int dispersionLength = isHorizontal ? outW : outH;
        final int averagingWidth = isHorizontal ? outH : outW;

        // The normalization factor is applied to all sums
        final double normalizationFactor = (double) (averagingWidth * Math.pow(4, nSpecDownsampleSteps));

        double[] dispersionSums = new double[dispersionLength];

        for (int pixelIndex = 0; pixelIndex < bytes.length / 4 ; pixelIndex++) {
            int byteIndex = pixelIndex * 4;
            int r = bytes[byteIndex] & 0xff;
            int g = bytes[byteIndex+1] & 0xff;
            long luminance  = (r << 8) + g;

            // Calculate the index along the dispersion axis
            int dispersionIndex = isHorizontal ? (pixelIndex % outW) : (pixelIndex / outW);

            dispersionSums[dispersionIndex] += (double) luminance;
        }

        // Normalize the final aggregated sums by the factor
        for (int i = 0; i < dispersionLength; i++) {
            dispersionSums[i] /= normalizationFactor;
        }

        // Calculate the normalized passepartout boundaries
        final float normalizedYMin = 1.0f - Math.min(passepartout.top, passepartout.bottom);
        final float normalizedYMax = 1.0f - Math.max(passepartout.top, passepartout.bottom);

        final float normalizedXMin = 1.0f - Math.min(passepartout.left, passepartout.right);
        final float normalizedXMax = 1.0f - Math.max(passepartout.left, passepartout.right);

        // Calculate the region of interest indices
        int roiStartIndex = (int) ((isHorizontal? normalizedYMax : normalizedXMax) * dispersionLength);
        int roiEndIndex = (int) ((isHorizontal? normalizedYMin : normalizedXMin) * dispersionLength);

        // Clamp indices to safe bounds
        roiStartIndex = Math.clamp(roiStartIndex, 0, dispersionSums.length);
        roiEndIndex = Math.clamp(roiEndIndex, 0, dispersionSums.length);

        if (roiStartIndex < roiEndIndex) {
            latestResult = Arrays.copyOfRange(dispersionSums, roiStartIndex, roiEndIndex);
        } else {
            // Handle edge case where indices might be flipped or equal
            latestResult = Arrays.copyOfRange(dispersionSums, roiEndIndex, roiStartIndex);
        }

    }

    @Override
    public void writeToBuffers(CameraSettingState state) {
        double exposureFactor = Math.pow(2.0, state.getCurrentApertureValue())/2.0 * 100.0/state.getCurrentIsoValue() *
                        (1.0e9/60.0) / state.getCurrentShutterValue();

        out.clear(true);
        pixelPosition.clear(true); // Clear pixel position buffer too

        Log.d("TEST", "Writing " + latestResult.length + " values.");

        if (latestResult != null) {
            for (int i = 0; i < latestResult.length; i++) {
                pixelPosition.append(i);
                out.append(latestResult[i] * exposureFactor);
            }
        }

        latestResult = null;
    }

    public void makeCurrent(EGLSurface eglSurface, int w, int h) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("Camera preview: eglMakeCurrent failed");
        }
        GLES20.glViewport(0, 0, w, h);
    }

    void drawLuminance(float[] camMatrix, RectF passepartout) {
        makeCurrent(analyzingSurface, width, height);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
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

        GLES20.glUniformMatrix4fv(spectroscopyProgramCamMatrixHandle, 1, false, camMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glDisableVertexAttribArray(spectroscopyProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(spectroscopyProgramTexCoordinatesHandle);

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        checkGLError("draw luminance");
    }

    void drawVerticalReduction(int step, float[] camMatrix) {
        makeCurrent(specDownsampleSurfaces[step], wSpecDownsampleStep[step], hSpecDownsampleStep[step]);

        GLES20.glUseProgram(verticalReductionProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(reductionProgramVerticesHandle);
        GLES20.glVertexAttribPointer(reductionProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(reductionProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(reductionProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, specDownsamplingTextures[step]);
        EGL14.eglBindTexImage(eglDisplay, (step == 0) ? analyzingSurface : specDownsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glUniform1i(reductionProgramTextureHandle, 0);

        GLES20.glUniform2f(reductionResSourceHandle, step == 0 ? width : wSpecDownsampleStep[step-1], step == 0 ? height : hSpecDownsampleStep[step-1]);
        GLES20.glUniform2f(reductionResTargetHandle, wSpecDownsampleStep[step], hSpecDownsampleStep[step]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        EGL14.eglReleaseTexImage(eglDisplay, (step == 0) ? analyzingSurface : specDownsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glDisableVertexAttribArray(reductionProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(reductionProgramTexCoordinatesHandle);

        checkGLError("vertical reduction");
    }
}
