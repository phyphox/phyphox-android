package de.rwth_aachen.phyphox.camera.analyzer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.buildProgram;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.checkGLError;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboTexCoordinates;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboVertices;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.interpolatingFullScreenVertexShader;

import android.graphics.RectF;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ThresholdAnalyzer extends AnalyzingModule {

    final static String thresholdFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision highp float;" +
                    "uniform samplerExternalOES texture;" +
                    "varying vec2 positionInPassepartout;" +
                    "varying vec2 texPosition;" +
                    "uniform float threshold;" +
                    "void main () {" +
                    "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
                    "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);" +
                    "  } else {" +
                    "    vec3 gammaRGB = texture2D(texture, texPosition).rgb;" +
                    "    float luma = dot(gammaRGB, vec3(0.2126, 0.7152, 0.0722));" +
                    "    gl_FragColor = vec4(0.0, 0.0, luma > threshold ? 1.0 : 0.0, 1.0);" +
                    " }" +
                    "}";

    final static String thresholdDownsamplingFragmentShader =
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
            "   if (texPosition3.y <= 1.0)" +
            "       result += texture2D(texture, texPosition3);" +
            "   if (texPosition4.x <= 1.0 && texPosition4.y <= 1.0)" +
            "       result += texture2D(texture, texPosition4);" +
            "   float overflow = floor(result.b);" +
            "   result.b = result.b - overflow;" +
            "   result.g = result.g + overflow / 255.0;" +
            "   gl_FragColor = result;" +
            "}";

    double threshold = 0.5;
    DataBuffer out;
    int thresholdProgram, thresholdDownsamplingProgram;
    int thresholdProgramThresholdHandle, thresholdProgramVerticesHandle, thresholdProgramTexCoordinatesHandle, thresholdProgramCamMatrixHandle, thresholdProgramTextureHandle, thresholdProgramPassepartoutMinHandle, thresholdProgramPassepartoutMaxHandle;
    int thresholdDownsamplingProgramVerticesHandle, thresholdDownsamplingProgramTexCoordinatesHandle, thresholdDownsamplingProgramTextureHandle;
    int thresholdDownsamplingResSourceHandle, thresholdDownsamplingResTargetHandle;

    double latestResult = Double.NaN;

    ByteBuffer resultBuffer = null;
    int resultBufferSize = 0;
    public ThresholdAnalyzer(DataBuffer out, double threshold) {
        this.threshold = threshold;
        this.out = out;
    }
    @Override
    public void prepare() {
        thresholdProgram = buildProgram(fullScreenVertexShader, thresholdFragmentShader);
        thresholdProgramThresholdHandle = GLES20.glGetUniformLocation(thresholdProgram, "threshold");
        thresholdProgramVerticesHandle = GLES20.glGetAttribLocation(thresholdProgram, "vertices");
        thresholdProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(thresholdProgram, "texCoordinates");
        thresholdProgramCamMatrixHandle = GLES20.glGetUniformLocation(thresholdProgram, "camMatrix");
        thresholdProgramTextureHandle = GLES20.glGetUniformLocation(thresholdProgram, "texture");
        thresholdProgramPassepartoutMinHandle = GLES20.glGetUniformLocation(thresholdProgram, "passepartoutMin");
        thresholdProgramPassepartoutMaxHandle = GLES20.glGetUniformLocation(thresholdProgram, "passepartoutMax");

        thresholdDownsamplingProgram = buildProgram(interpolatingFullScreenVertexShader, thresholdDownsamplingFragmentShader);
        thresholdDownsamplingProgramVerticesHandle = GLES20.glGetAttribLocation(thresholdDownsamplingProgram, "vertices");
        thresholdDownsamplingProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(thresholdDownsamplingProgram, "texCoordinates");
        thresholdDownsamplingProgramTextureHandle = GLES20.glGetUniformLocation(thresholdDownsamplingProgram, "texture");
        thresholdDownsamplingResSourceHandle = GLES20.glGetUniformLocation(thresholdDownsamplingProgram, "resSource");
        thresholdDownsamplingResTargetHandle = GLES20.glGetUniformLocation(thresholdDownsamplingProgram, "resTarget");

        checkGLError("ThresholdAnalyzer: prepare");
    }

    @Override
    public void analyze(float[] camMatrix, RectF passepartout) {
        drawThreshold(camMatrix, passepartout);
        for (int i = 0; i < nDownsampleSteps; i++) {
            drawThresholdDownsampling(i, camMatrix);
        }

        int outW = wDownsampleStep[nDownsampleSteps -1];
        int outH = hDownsampleStep[nDownsampleSteps -1];

        long totalContribution = 0;

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
            totalContribution += ((g << 8) + b);
        }

        checkGLError("threshold analyze");

        latestResult = Math.round((double)(totalContribution*Math.pow(4, nDownsampleSteps))/255.0);
    }

    @Override
    public void writeToBuffers(CameraSettingState state) {
        out.append(latestResult);
    }

    public void makeCurrent(EGLSurface eglSurface, int w, int h) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("Camera preview: eglMakeCurrent failed");
        }
        GLES20.glViewport(0,0, w, h);
    }

    void drawThreshold(float[] camMatrix, RectF passepartout) {
        makeCurrent(analyzingSurface, w, h);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor((int)Math.floor(w*(1.0-Math.max(passepartout.top, passepartout.bottom))), (int)Math.floor(h*(1.0-Math.max(passepartout.left, passepartout.right))), (int)Math.ceil(w*Math.abs(passepartout.height())), (int)Math.ceil(h*Math.abs(passepartout.width())));

        GLES20.glUseProgram(thresholdProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(thresholdProgramVerticesHandle);
        GLES20.glVertexAttribPointer(thresholdProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(thresholdProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(thresholdProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glUniform1i(thresholdProgramTextureHandle, 0);

        GLES20.glUniform2f(thresholdProgramPassepartoutMinHandle, passepartout.left, passepartout.top);
        GLES20.glUniform2f(thresholdProgramPassepartoutMaxHandle, passepartout.right, passepartout.bottom);

        GLES20.glUniform1f(thresholdProgramThresholdHandle, (float)threshold);

        GLES20.glUniformMatrix4fv(thresholdProgramCamMatrixHandle, 1, false, camMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(thresholdProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(thresholdProgramTexCoordinatesHandle);

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        checkGLError("draw threshold");
    }

    void drawThresholdDownsampling(int step, float[] camMatrix) {
        long start = System.nanoTime();
        makeCurrent(downsampleSurfaces[step], wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glUseProgram(thresholdDownsamplingProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(thresholdDownsamplingProgramVerticesHandle);
        GLES20.glVertexAttribPointer(thresholdDownsamplingProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(thresholdDownsamplingProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(thresholdDownsamplingProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, downsamplingTextures[step]);
        EGL14.eglBindTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glUniform1i(thresholdDownsamplingProgramTextureHandle, 0);

        GLES20.glUniform2f(thresholdDownsamplingResSourceHandle, step == 0 ? w : wDownsampleStep[step-1], step == 0 ? h : hDownsampleStep[step-1]);
        GLES20.glUniform2f(thresholdDownsamplingResTargetHandle, wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        EGL14.eglReleaseTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glDisableVertexAttribArray(thresholdDownsamplingProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(thresholdDownsamplingProgramTexCoordinatesHandle);

        checkGLError("downsample threshold");
    }
}
