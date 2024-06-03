package de.rwth_aachen.phyphox.camera.analyzer;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.buildProgram;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.checkGLError;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboTexCoordinates;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboVertices;

import android.graphics.RectF;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ExposureAnalyzer extends AnalyzingModule {

    final static String downsamplingFullScreenVertexShader =
            "precision highp float;" +
            "attribute vec2 vertices;" +
            "attribute vec2 texCoordinates;" +
            "uniform vec2 resSource;" +
            "uniform vec2 resTarget;" +
            "uniform mat4 camMatrix;" +
            "uniform vec2 passepartoutMin;" +
            "uniform vec2 passepartoutMax;" +
            "varying vec2 positionInPassepartout;" +
            "varying vec2 texPosition1;" +
            "varying vec2 texPosition2;" +
            "varying vec2 texPosition3;" +
            "varying vec2 texPosition4;" +
            "void main () {" +
            "   float x1 = (4.0*resTarget.x*texCoordinates.x - 1.0)/resSource.x;" +
            "   float x2 = (4.0*resTarget.x*texCoordinates.x + 1.0)/resSource.x;" +
            "   float y1 = (4.0*resTarget.y*texCoordinates.y - 1.0)/resSource.y;" +
            "   float y2 = (4.0*resTarget.y*texCoordinates.y + 1.0)/resSource.y;" +
            "   texPosition1 = vec2(x1, y1);" +
            "   texPosition2 = vec2(x2, y1);" +
            "   texPosition3 = vec2(x1, y2);" +
            "   texPosition4 = vec2(x2, y2);" +
            "   positionInPassepartout = vec2((0.5*(1.0-vertices.x) - passepartoutMin.y)/(passepartoutMax.y-passepartoutMin.y)," +
            "(0.5*(1.0-vertices.y) - passepartoutMin.x)/(passepartoutMax.x-passepartoutMin.x));" +
            "   gl_Position = vec4(vertices, 0., 1.);" +
            "}";


    final static String exposureFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;" +
            "uniform samplerExternalOES texture;" +
            "varying vec2 positionInPassepartout;" +
            "varying vec2 texPosition1;" +
            "varying vec2 texPosition2;" +
            "varying vec2 texPosition3;" +
            "varying vec2 texPosition4;" +
            "void main () {" +
            "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
            "    gl_FragColor = vec4(1.0, 0.0, 0.0, 0.0);" +
            "  } else {" +
            "    vec4 rgb = texture2D(texture, texPosition1);" +
            "    if (texPosition2.x <= 1.0)" +
            "       rgb += texture2D(texture, texPosition2);" +
            "    if (texPosition3.y <= 1.0)" +
            "       rgb += texture2D(texture, texPosition3);" +
            "    if (texPosition4.x <= 1.0 && texPosition4.y <= 1.0)" +
            "       rgb += texture2D(texture, texPosition4);" +
            "    rgb /= rgb.a;" +
            "    float luma = dot(rgb.rgb, vec3(0.2126, 0.7152, 0.0722));" +
            "    float rgbMax = max(rgb.r, max(rgb.b, rgb.g));" +
            "    float rgbMin = min(rgb.r, min(rgb.b, rgb.g));" +
            "    gl_FragColor = vec4(rgbMin, luma, rgbMax, 1.0);" +
            "  }" +
            "}";

    final static String samplingFullScreenVertexShader =
            "precision highp float;" +
            "attribute vec2 vertices;" +
            "attribute vec2 texCoordinates;" +
            "uniform vec2 resSource;" +
            "uniform vec2 resTarget;" +
            "varying float x1, x2, x3, x4, y1, y2, y3, y4;" +
            "void main () {" +
            "   x1 = (4.0*resTarget.x*texCoordinates.x - 1.5)/resSource.x;" +
            "   x2 = (4.0*resTarget.x*texCoordinates.x - 0.5)/resSource.x;" +
            "   x3 = (4.0*resTarget.x*texCoordinates.x + 0.5)/resSource.x;" +
            "   x4 = (4.0*resTarget.x*texCoordinates.x + 1.5)/resSource.x;" +
            "   y1 = (4.0*resTarget.y*texCoordinates.y - 1.5)/resSource.y;" +
            "   y2 = (4.0*resTarget.y*texCoordinates.y - 0.5)/resSource.y;" +
            "   y3 = (4.0*resTarget.y*texCoordinates.y + 0.5)/resSource.y;" +
            "   y4 = (4.0*resTarget.y*texCoordinates.y + 1.5)/resSource.y;" +
            "   gl_Position = vec4(vertices, 0., 1.);" +
            "}";
    final static String exposureDownsamplingFragmentShader =
            "precision highp float;" +
            "uniform sampler2D texture;" +
            "varying float x1, x2, x3, x4, y1, y2, y3, y4;" +
            "vec4 result;" +

            "void pick(float x, float y) {" +
            "  if (x > 1.0 || y > 1.0)" +
            "    return;" +
            "  vec4 sample = texture2D(texture, vec2(x, y));" +
            "  result.r = min(result.r, sample.r);" +
            "  result.g += sample.g * sample.a;" +
            "  result.b = max(result.b, sample.b);" +
            "  result.a += sample.a;" +
            "}" +

            "void main () {" +
            "   result = vec4(1.0, 0.0, 0.0, 0.0);" +
            "   pick(x1, y1);" +
            "   pick(x2, y1);" +
            "   pick(x3, y1);" +
            "   pick(x4, y1);" +
            "   pick(x1, y2);" +
            "   pick(x2, y2);" +
            "   pick(x3, y2);" +
            "   pick(x4, y2);" +
            "   pick(x1, y3);" +
            "   pick(x2, y3);" +
            "   pick(x3, y3);" +
            "   pick(x4, y3);" +
            "   pick(x1, y4);" +
            "   pick(x2, y4);" +
            "   pick(x3, y4);" +
            "   pick(x4, y4);" +
            "   result.g /= result.a;" +
            "   result.a = result.a / 16.0;" +
            "   gl_FragColor = result;" +
            "}";

    int exposureProgram, exposureDownsamplingProgram;
    int exposureProgramVerticesHandle, exposureProgramTexCoordinatesHandle, exposureProgramCamMatrixHandle, exposureProgramResSourceHandle, exposureProgramResTargetHandle, exposureProgramTextureHandle, exposureProgramPassepartoutMinHandle, exposureProgramPassepartoutMaxHandle;
    int exposureDownsamplingProgramVerticesHandle, exposureDownsamplingProgramTexCoordinatesHandle, exposureDownsamplingProgramTextureHandle;
    int exposureDownsamplingResSourceHandle, exposureDownsamplingResTargetHandle;

    public double minRGB = Double.NaN;
    public double maxRGB = Double.NaN;
    public double meanLuma = Double.NaN;

    public ExposureAnalyzer() {

    }
    @Override
    public void prepare() {
        exposureProgram = buildProgram(downsamplingFullScreenVertexShader, exposureFragmentShader);
        exposureProgramVerticesHandle = GLES20.glGetAttribLocation(exposureProgram, "vertices");
        exposureProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(exposureProgram, "texCoordinates");
        exposureProgramCamMatrixHandle = GLES20.glGetUniformLocation(exposureProgram, "camMatrix");
        exposureProgramResSourceHandle = GLES20.glGetUniformLocation(exposureProgram, "resSource");
        exposureProgramResTargetHandle = GLES20.glGetUniformLocation(exposureProgram, "resTarget");
        exposureProgramTextureHandle = GLES20.glGetUniformLocation(exposureProgram, "texture");
        exposureProgramPassepartoutMinHandle = GLES20.glGetUniformLocation(exposureProgram, "passepartoutMin");
        exposureProgramPassepartoutMaxHandle = GLES20.glGetUniformLocation(exposureProgram, "passepartoutMax");

        exposureDownsamplingProgram = buildProgram(samplingFullScreenVertexShader, exposureDownsamplingFragmentShader);
        exposureDownsamplingProgramVerticesHandle = GLES20.glGetAttribLocation(exposureDownsamplingProgram, "vertices");
        exposureDownsamplingProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(exposureDownsamplingProgram, "texCoordinates");
        exposureDownsamplingProgramTextureHandle = GLES20.glGetUniformLocation(exposureDownsamplingProgram, "texture");
        exposureDownsamplingResSourceHandle = GLES20.glGetUniformLocation(exposureDownsamplingProgram, "resSource");
        exposureDownsamplingResTargetHandle = GLES20.glGetUniformLocation(exposureDownsamplingProgram, "resTarget");

        checkGLError("ExposureAnalyzer: prepare");
    }

    @Override
    public void analyze(float[] camMatrix, RectF passepartout) {
        drawExposure(camMatrix, passepartout);
        for (int i = 1; i < nDownsampleSteps; i++) {
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

        checkGLError("exposure analyze");

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
        makeCurrent(downsampleSurfaces[0], wDownsampleStep[0], hDownsampleStep[0]);

        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor((int)Math.floor(wDownsampleStep[0]*(1.0-Math.max(passepartout.top, passepartout.bottom))), (int)Math.floor(hDownsampleStep[0]*(1.0-Math.max(passepartout.left, passepartout.right))), (int)Math.ceil(wDownsampleStep[0]*Math.abs(passepartout.height())), (int)Math.ceil(hDownsampleStep[0]*Math.abs(passepartout.width())));

        GLES20.glUseProgram(exposureProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(exposureProgramVerticesHandle);
        GLES20.glVertexAttribPointer(exposureProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(exposureProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(exposureProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glUniform1i(exposureProgramTextureHandle, 0);

        GLES20.glUniform2f(exposureProgramPassepartoutMinHandle, passepartout.left, passepartout.top);
        GLES20.glUniform2f(exposureProgramPassepartoutMaxHandle, passepartout.right, passepartout.bottom);

        GLES20.glUniformMatrix4fv(exposureProgramCamMatrixHandle, 1, false, camMatrix, 0);

        GLES20.glUniform2f(exposureProgramResSourceHandle, w, h);
        GLES20.glUniform2f(exposureProgramResTargetHandle, wDownsampleStep[0], hDownsampleStep[0]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(exposureProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(exposureProgramTexCoordinatesHandle);

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        checkGLError("draw exposure");
    }

    void drawExposureDownsampling(int step, float[] camMatrix) {
        long start = System.nanoTime();
        makeCurrent(downsampleSurfaces[step], wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glUseProgram(exposureDownsamplingProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(exposureDownsamplingProgramVerticesHandle);
        GLES20.glVertexAttribPointer(exposureDownsamplingProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(exposureDownsamplingProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(exposureDownsamplingProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, downsamplingTextures[step]);
        EGL14.eglBindTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glUniform1i(exposureDownsamplingProgramTextureHandle, 0);

        GLES20.glUniform2f(exposureDownsamplingResSourceHandle, step == 0 ? w : wDownsampleStep[step-1], step == 0 ? h : hDownsampleStep[step-1]);
        GLES20.glUniform2f(exposureDownsamplingResTargetHandle, wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        EGL14.eglReleaseTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glDisableVertexAttribArray(exposureDownsamplingProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(exposureDownsamplingProgramTexCoordinatesHandle);

        checkGLError("downsample exposure");
    }
}
