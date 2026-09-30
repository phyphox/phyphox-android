package de.rwth_aachen.phyphox.camera.analyzer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.buildProgram;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.checkGLError;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboTexCoordinates;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboVertices;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.interpolatingFullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.meanDownsamplingFragmentShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.packedMeanFunctions;

import android.graphics.RectF;
import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

public class LuminanceAnalyzer extends AnalyzingModule {

    final static String luminanceFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;" +
            "uniform samplerExternalOES texture;" +
            "uniform vec3 weights;" +
            "varying vec2 positionInPassepartout;" +
            "varying vec2 texPosition;" +
            packedMeanFunctions +

            "float linearize(float x) {" +
            "  if (x < 0.04045) " +
            "    return x/12.92;" +
            "  else" +
            "    return pow((x+0.055)/1.055, 2.4);" +
            "}" +

            "void main () {" +
            "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
            "    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);" +
            "  } else {" +
            "    vec3 gammaRGB = texture2D(texture, texPosition).rgb;" +

//            "    vec3 linRGB = pow(gammaRGB, vec3(2.2, 2.2, 2.2));" +   //Adobe RGB or approximation of sRGB

            "    vec3 linRGB = vec3(linearize(gammaRGB.r), linearize(gammaRGB.g), linearize(gammaRGB.b));" +
            "    gl_FragColor = packMean(vec2(dot(linRGB, weights), 1.0));" +
            "  }" +
            "}";

    final static String lumaFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision highp float;" +
                    "uniform samplerExternalOES texture;" +
                    "uniform vec3 weights;" +
                    "varying vec2 positionInPassepartout;" +
                    "varying vec2 texPosition;" +
                    packedMeanFunctions +
                    "void main () {" +
                    "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
                    "    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);" +
                    "  } else {" +
                    "    vec3 gammaRGB = texture2D(texture, texPosition).rgb;" +
                    "    gl_FragColor = packMean(vec2(dot(gammaRGB, weights), 1.0));" +
                    " }" +
                    "}";


    //Weights of the per-pixel dot product: BT.709 for luma/luminance, a unit vector for a single colour channel.
    //Both shader variants take them as a uniform, so the same reduction chain serves all four outputs.
    public enum Channel {
        luma(0.2126f, 0.7152f, 0.0722f),
        red(1.0f, 0.0f, 0.0f),
        green(0.0f, 1.0f, 0.0f),
        blue(0.0f, 0.0f, 1.0f);

        final float[] weights;
        Channel(float r, float g, float b) {
            weights = new float[]{r, g, b};
        }
    }

    boolean linear = false;
    Channel channel = Channel.luma;
    DataBuffer out;
    int luminanceProgram, luminanceDownsamplingProgram;
    int luminanceProgramVerticesHandle, luminanceProgramTexCoordinatesHandle, luminanceProgramCamMatrixHandle, luminanceProgramTextureHandle, luminanceProgramPassepartoutMinHandle, luminanceProgramPassepartoutMaxHandle, luminanceProgramWeightsHandle;
    int luminanceDownsamplingProgramVerticesHandle, luminanceDownsamplingProgramTexCoordinatesHandle, luminanceDownsamplingProgramTextureHandle;
    int luminanceDownsamplingResSourceHandle, luminanceDownsamplingResTargetHandle;

    double latestResult = Double.NaN;

    ByteBuffer resultBuffer = null;
    int resultBufferSize = 0;
    public LuminanceAnalyzer(DataBuffer out, boolean linear) {
        this(out, linear, Channel.luma);
    }

    public LuminanceAnalyzer(DataBuffer out, boolean linear, Channel channel) {
        this.linear = linear;
        this.channel = channel;
        this.out = out;
    }
    @Override
    public void prepare() {
        luminanceProgram = buildProgram(fullScreenVertexShader, linear ? luminanceFragmentShader : lumaFragmentShader);
        luminanceProgramVerticesHandle = GLES20.glGetAttribLocation(luminanceProgram, "vertices");
        luminanceProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(luminanceProgram, "texCoordinates");
        luminanceProgramCamMatrixHandle = GLES20.glGetUniformLocation(luminanceProgram, "camMatrix");
        luminanceProgramTextureHandle = GLES20.glGetUniformLocation(luminanceProgram, "texture");
        luminanceProgramPassepartoutMinHandle = GLES20.glGetUniformLocation(luminanceProgram, "passepartoutMin");
        luminanceProgramPassepartoutMaxHandle = GLES20.glGetUniformLocation(luminanceProgram, "passepartoutMax");
        luminanceProgramWeightsHandle = GLES20.glGetUniformLocation(luminanceProgram, "weights");

        luminanceDownsamplingProgram = buildProgram(interpolatingFullScreenVertexShader, meanDownsamplingFragmentShader);
        luminanceDownsamplingProgramVerticesHandle = GLES20.glGetAttribLocation(luminanceDownsamplingProgram, "vertices");
        luminanceDownsamplingProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(luminanceDownsamplingProgram, "texCoordinates");
        luminanceDownsamplingProgramTextureHandle = GLES20.glGetUniformLocation(luminanceDownsamplingProgram, "texture");
        luminanceDownsamplingResSourceHandle = GLES20.glGetUniformLocation(luminanceDownsamplingProgram, "resSource");
        luminanceDownsamplingResTargetHandle = GLES20.glGetUniformLocation(luminanceDownsamplingProgram, "resTarget");

        checkGLError("LuminanceAnalyzer: prepare");
    }

    @Override
    public void analyze(float[] camMatrix, RectF passepartout) {
        drawLuminance(camMatrix, passepartout);
        for (int i = 0; i < nDownsampleSteps; i++) {
            drawLuminanceDownsampling(i, camMatrix);
        }

        int outW = wDownsampleStep[nDownsampleSteps -1];
        int outH = hDownsampleStep[nDownsampleSteps -1];

        long value = 0;
        long coverage = 0;

        if (resultBuffer == null || resultBufferSize != outH * outW) {
            resultBufferSize = outH*outW;
            resultBuffer = ByteBuffer.allocateDirect(resultBufferSize * 4).order(ByteOrder.nativeOrder());
        }
        resultBuffer.rewind();

        GLES20.glReadPixels(0, 0, outW, outH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, resultBuffer);

        resultBuffer.rewind();
        while (resultBuffer.hasRemaining()) {
            long r = resultBuffer.get() & 0xff;
            long g = resultBuffer.get() & 0xff;
            long b = resultBuffer.get() & 0xff;
            long a = resultBuffer.get() & 0xff;
            value += r * 255 + g;
            coverage += b * 255 + a;
        }

        checkGLError("luminance analyze");

        latestResult = coverage == 0 ? Double.NaN : (double)value / (double)coverage;
    }

    @Override
    public void writeToBuffers(CameraSettingState state) {
        double exposureFactor = linear ? Math.pow(2.0, state.getCurrentApertureValue())/2.0 * 100.0/state.getCurrentIsoValue() * (1.0e9/60.0) / state.getCurrentShutterValue() : 1.0;
        out.append(latestResult*exposureFactor);
    }

    void drawLuminance(float[] camMatrix, RectF passepartout) {
        makeCurrent(analyzingFramebuffer, width, height);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(
                (int)Math.floor(width*(1.0-Math.max(passepartout.top, passepartout.bottom))),
                (int)Math.floor(height*(1.0-Math.max(passepartout.left, passepartout.right))),
                (int)Math.ceil(width*Math.abs(passepartout.height())),
                (int)Math.ceil(height*Math.abs(passepartout.width())));

        GLES20.glUseProgram(luminanceProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(luminanceProgramVerticesHandle);
        GLES20.glVertexAttribPointer(luminanceProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(luminanceProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(luminanceProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glUniform1i(luminanceProgramTextureHandle, 0);

        GLES20.glUniform2f(luminanceProgramPassepartoutMinHandle, passepartout.left, passepartout.top);
        GLES20.glUniform2f(luminanceProgramPassepartoutMaxHandle, passepartout.right, passepartout.bottom);
        GLES20.glUniform3fv(luminanceProgramWeightsHandle, 1, channel.weights, 0);

        GLES20.glUniformMatrix4fv(luminanceProgramCamMatrixHandle, 1, false, camMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(luminanceProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(luminanceProgramTexCoordinatesHandle);

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        checkGLError("draw luminance");
    }

    void drawLuminanceDownsampling(int step, float[] camMatrix) {
        long start = System.nanoTime();
        makeCurrent(downsamplingFramebuffers[step], wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glUseProgram(luminanceDownsamplingProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(luminanceDownsamplingProgramVerticesHandle);
        GLES20.glVertexAttribPointer(luminanceDownsamplingProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(luminanceDownsamplingProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(luminanceDownsamplingProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, (step == 0) ? analyzingTexture : downsamplingTextures[step-1]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glUniform1i(luminanceDownsamplingProgramTextureHandle, 0);

        GLES20.glUniform2f(luminanceDownsamplingResSourceHandle, step == 0 ? width : wDownsampleStep[step-1], step == 0 ? height : hDownsampleStep[step-1]);
        GLES20.glUniform2f(luminanceDownsamplingResTargetHandle, wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(luminanceDownsamplingProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(luminanceDownsamplingProgramTexCoordinatesHandle);

        checkGLError("downsample luminance");
    }
}
