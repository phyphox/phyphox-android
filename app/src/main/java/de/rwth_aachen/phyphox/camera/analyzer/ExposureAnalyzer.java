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

import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ExposureAnalyzer extends AnalyzingModule {

    final static String exposureFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;" +
            "uniform samplerExternalOES texture;" +
            "varying vec2 positionInPassepartout;" +
            "varying vec2 texPosition;" +
            "void main () {" +
            "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
            "    gl_FragColor = vec4(1.0, 0.0, 0.0, 0.0);" +
            "  } else {" +
            "    vec3 rgb = texture2D(texture, texPosition).rgb;" +
            "    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));" +
            "    float rgbMax = max(rgb.r, max(rgb.b, rgb.g));" +
            "    float rgbMin = min(rgb.r, min(rgb.b, rgb.g));" +
            "    gl_FragColor = vec4(rgbMin, luma, rgbMax, 1.0);" +
            "  }" +
            "}";

    final static String exposureDownsamplingFragmentShader =
            "precision highp float;" +
            "uniform sampler2D texture;" +
            "varying vec2 texPosition1;" +
            "varying vec2 texPosition2;" +
            "varying vec2 texPosition3;" +
            "varying vec2 texPosition4;" +
            "void main () {" +
            "   vec4 sample = texture2D(texture, texPosition1);" +
            "   vec4 result = sample;" +
            "   result.g *= result.a;" +
            "   if (texPosition2.x <= 1.0) {" +
            "       sample = texture2D(texture, texPosition2);" +
            "       result.r = min(result.r, sample.r);" +
            "       result.g += sample.g * sample.a;" +
            "       result.b = max(result.b, sample.b);" +
            "       result.a += sample.a;" +
            "   }" +
            "   if (texPosition3.y <= 1.0) {" +
            "       sample = texture2D(texture, texPosition3);" +
            "       result.r = min(result.r, sample.r);" +
            "       result.g += sample.g * sample.a;" +
            "       result.b = max(result.b, sample.b);" +
            "       result.a += sample.a;" +
            "   }" +
            "   if (texPosition4.x <= 1.0 && texPosition4.y <= 1.0) {" +
            "       sample = texture2D(texture, texPosition4);" +
            "       result.r = min(result.r, sample.r);" +
            "       result.g += sample.g * sample.a;" +
            "       result.b = max(result.b, sample.b);" +
            "       result.a += sample.a;" +
            "   }" +
            "   result.g /= result.a;" +
            "   result.a = result.a / 4.0;" +
            "   gl_FragColor = result;" +
            "}";

    int luminanceProgram, luminanceDownsamplingProgram;
    int luminanceProgramVerticesHandle, luminanceProgramTexCoordinatesHandle, luminanceProgramCamMatrixHandle, luminanceProgramTextureHandle, luminanceProgramPassepartoutMinHandle, luminanceProgramPassepartoutMaxHandle;
    int luminanceDownsamplingProgramVerticesHandle, luminanceDownsamplingProgramTexCoordinatesHandle, luminanceDownsamplingProgramTextureHandle;
    int luminanceDownsamplingResSourceHandle, luminanceDownsamplingResTargetHandle;

    public double minRGB = Double.NaN;
    public double maxRGB = Double.NaN;
    public double meanLuma = Double.NaN;

    public ExposureAnalyzer() {

    }
    @Override
    public void prepare() {
        luminanceProgram = buildProgram(fullScreenVertexShader, exposureFragmentShader);
        luminanceProgramVerticesHandle = GLES20.glGetAttribLocation(luminanceProgram, "vertices");
        luminanceProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(luminanceProgram, "texCoordinates");
        luminanceProgramCamMatrixHandle = GLES20.glGetUniformLocation(luminanceProgram, "camMatrix");
        luminanceProgramTextureHandle = GLES20.glGetUniformLocation(luminanceProgram, "texture");
        luminanceProgramPassepartoutMinHandle = GLES20.glGetUniformLocation(luminanceProgram, "passepartoutMin");
        luminanceProgramPassepartoutMaxHandle = GLES20.glGetUniformLocation(luminanceProgram, "passepartoutMax");

        luminanceDownsamplingProgram = buildProgram(interpolatingFullScreenVertexShader, exposureDownsamplingFragmentShader);
        luminanceDownsamplingProgramVerticesHandle = GLES20.glGetAttribLocation(luminanceDownsamplingProgram, "vertices");
        luminanceDownsamplingProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(luminanceDownsamplingProgram, "texCoordinates");
        luminanceDownsamplingProgramTextureHandle = GLES20.glGetUniformLocation(luminanceDownsamplingProgram, "texture");
        luminanceDownsamplingResSourceHandle = GLES20.glGetUniformLocation(luminanceDownsamplingProgram, "resSource");
        luminanceDownsamplingResTargetHandle = GLES20.glGetUniformLocation(luminanceDownsamplingProgram, "resTarget");

        checkGLError("ExposureAnalyzer: prepare");
    }

    @Override
    public void analyze(float[] camMatrix, RectF passepartout) {
        drawExposure(camMatrix, passepartout);
        for (int i = 0; i < nDownsampleSteps; i++) {
            drawExposureDownsampling(i, camMatrix);
        }

        int outW = wDownsampleStep[nDownsampleSteps -1];
        int outH = hDownsampleStep[nDownsampleSteps -1];

        long vMin = 255;
        long vMean = 0;
        long vMax = 0;
        long totalContribution = 0;

        ByteBuffer resultBuffer = ByteBuffer.allocateDirect(outW * outH * 4).order(ByteOrder.nativeOrder());
        resultBuffer.rewind();

        GLES20.glReadPixels(0, 0, outW, outH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, resultBuffer);

        resultBuffer.rewind();
        while (resultBuffer.hasRemaining()) {
            long r = resultBuffer.get() & 0xff;
            long g = resultBuffer.get() & 0xff;
            long b = resultBuffer.get() & 0xff;
            long a = resultBuffer.get() & 0xff;
            vMin = Math.min(vMin, r);
            vMean += g * a;
            vMax = Math.max(vMax, b);
            totalContribution += a;
        }

        checkGLError("luminance analyze");

        minRGB = vMin / 255.0f;
        maxRGB = vMax / 255.0f;
        meanLuma = (double)vMean / (double)(totalContribution) / 255.0f;
    }

    public void reset() {
        minRGB = Double.NaN;
        maxRGB = Double.NaN;
        meanLuma = Double.NaN;
    }

    @Override
    public void writeToBuffers(CameraSettingState state) {
    }

    public void makeCurrent(EGLSurface eglSurface, int w, int h) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("Camera preview: eglMakeCurrent failed");
        }
        GLES20.glViewport(0,0, w, h);
    }

    void drawExposure(float[] camMatrix, RectF passepartout) {
        makeCurrent(analyzingSurface, w, h);

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

        GLES20.glUniformMatrix4fv(luminanceProgramCamMatrixHandle, 1, false, camMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(luminanceProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(luminanceProgramTexCoordinatesHandle);

        checkGLError("draw exposure");
    }

    void drawExposureDownsampling(int step, float[] camMatrix) {
        long start = System.nanoTime();
        makeCurrent(downsampleSurfaces[step], wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glUseProgram(luminanceDownsamplingProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(luminanceDownsamplingProgramVerticesHandle);
        GLES20.glVertexAttribPointer(luminanceDownsamplingProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(luminanceDownsamplingProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(luminanceDownsamplingProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, downsamplingTextures[step]);
        EGL14.eglBindTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glUniform1i(luminanceDownsamplingProgramTextureHandle, 0);

        GLES20.glUniform2f(luminanceDownsamplingResSourceHandle, step == 0 ? w : wDownsampleStep[step-1], step == 0 ? h : hDownsampleStep[step-1]);
        GLES20.glUniform2f(luminanceDownsamplingResTargetHandle, wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        EGL14.eglReleaseTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glDisableVertexAttribArray(luminanceDownsamplingProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(luminanceDownsamplingProgramTexCoordinatesHandle);

        checkGLError("downsample exposure");
    }
}
