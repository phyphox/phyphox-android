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
import de.rwth_aachen.phyphox.Helper.RGB;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class HSVAnalyzer extends AnalyzingModule {

    final static String hueFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;" +
            "uniform samplerExternalOES texture;" +
            "varying vec2 positionInPassepartout;" +
            "varying vec2 texPosition;" +
            "void main () {" +
            "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
            "    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);" +
            "  } else {" +
            "    vec3 rgb = texture2D(texture, texPosition).rgb;" +
            "    float rgbMax = max(max(rgb.r, rgb.g), rgb.b);" +
            "    float rgbMin = min(min(rgb.r, rgb.g), rgb.b);" +
            "    float d = rgbMax - rgbMin;" +
            "    float h;" +
            "    if (rgbMax == rgbMin) {" +
            "      h = 0.0;" +
            "    } else if (rgbMax == rgb.r) {" +
            "      h = (rgb.g - rgb.b + d * (rgb.g < rgb.b ? 6.0 : 0.0)) / (6.0 * d);" +
            "    } else if (rgbMax == rgb.g) {" +
            "      h = (rgb.b - rgb.r + d * 2.0) / (6.0 * d);" +
            "    } else {" +
            "      h = (rgb.r - rgb.g + d * 4.0) / (6.0 * d);" +
            "    }" +
            "    gl_FragColor = vec4(0.0, sin(h), 0.0, cos(h));" +
            "  }" +
            "}";

    final static String saturationFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision highp float;" +
                    "uniform samplerExternalOES texture;" +
                    "varying vec2 positionInPassepartout;" +
                    "varying vec2 texPosition;" +
                    "void main () {" +
                    "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
                    "    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);" +
                    "  } else {" +
                    "    float saturation;" +
                    "    vec3 rgb = texture2D(texture, texPosition).rgb;" +
                    "    float rgbMax = max(max(rgb.r, rgb.g), rgb.b);" +
                    "    float rgbMin = min(min(rgb.r, rgb.g), rgb.b);" +
                    "    float d = rgbMax - rgbMin;" +
                    "    if (rgbMax == 0.0) " +
                    "      saturation = 0.0;" +
                    "    else" +
                    "      saturation = d / rgbMax;" +
                    "    gl_FragColor = vec4(0.0, saturation, 1.0, 1.0);" +
                    " }" +
                    "}";

    final static String valueFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision highp float;" +
                    "uniform samplerExternalOES texture;" +
                    "varying vec2 positionInPassepartout;" +
                    "varying vec2 texPosition;" +
                    "void main () {" +
                    "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
                    "    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);" +
                    "  } else {" +
                    "    vec3 rgb = texture2D(texture, texPosition).rgb;" +
                    "    float value = max(rgb.r, max(rgb.b, rgb.g));" +
                    "    gl_FragColor = vec4(0.0, value, 1.0, 1.0);" +
                    " }" +
                    "}";

    final static String hueDownsamplingFragmentShader =
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
            "   float overflow = floor(result.g);" +
            "   result.g = result.g - overflow;" +
            "   result.r = result.r + overflow / 255.0;" +
            "   overflow = floor(result.a);" +
            "   result.a = result.a - overflow;" +
            "   result.b = result.b + overflow / 255.0;" +
            "   gl_FragColor = result;" +
            "}";

    final static String saturationValueDownsamplingFragmentShader =
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
                    "   float overflow = floor(result.g);" +
                    "   result.g = result.g - overflow;" +
                    "   result.r = result.r + overflow / 255.0;" +
                    "   result.b = result.b / 4.0;" +
                    "   gl_FragColor = result;" +
                    "}";

    enum Mode {
        hue, saturation, value
    }
    Mode mode;
    DataBuffer out;
    int hsvProgram, hsvDownsamplingProgram;
    int hsvProgramVerticesHandle, hsvProgramTexCoordinatesHandle, hsvProgramCamMatrixHandle, hsvProgramTextureHandle, hsvProgramPassepartoutMinHandle, hsvProgramPassepartoutMaxHandle;
    int hsvDownsamplingProgramVerticesHandle, hsvDownsamplingProgramTexCoordinatesHandle, hsvDownsamplingProgramTextureHandle;
    int hsvDownsamplingResSourceHandle, hsvDownsamplingResTargetHandle;

    double latestResult = Double.NaN;
    public HSVAnalyzer(DataBuffer out, Mode mode) {
        this.mode = mode;
        this.out = out;
    }
    @Override
    public void prepare() {
        switch (mode) {
            case hue:
                hsvProgram = buildProgram(fullScreenVertexShader, hueFragmentShader);
                break;
            case saturation:
                hsvProgram = buildProgram(fullScreenVertexShader, saturationFragmentShader);
                break;
            case value:
                hsvProgram = buildProgram(fullScreenVertexShader, valueFragmentShader);
                break;
        }
        hsvProgramVerticesHandle = GLES20.glGetAttribLocation(hsvProgram, "vertices");
        hsvProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(hsvProgram, "texCoordinates");
        hsvProgramCamMatrixHandle = GLES20.glGetUniformLocation(hsvProgram, "camMatrix");
        hsvProgramTextureHandle = GLES20.glGetUniformLocation(hsvProgram, "texture");
        hsvProgramPassepartoutMinHandle = GLES20.glGetUniformLocation(hsvProgram, "passepartoutMin");
        hsvProgramPassepartoutMaxHandle = GLES20.glGetUniformLocation(hsvProgram, "passepartoutMax");

        switch (mode) {
            case hue:
                hsvDownsamplingProgram = buildProgram(interpolatingFullScreenVertexShader, hueDownsamplingFragmentShader);
                break;
            case saturation:
                hsvDownsamplingProgram = buildProgram(interpolatingFullScreenVertexShader, saturationValueDownsamplingFragmentShader);
                break;
            case value:
                hsvDownsamplingProgram = buildProgram(interpolatingFullScreenVertexShader, saturationValueDownsamplingFragmentShader);
                break;
        }
        hsvDownsamplingProgramVerticesHandle = GLES20.glGetAttribLocation(hsvDownsamplingProgram, "vertices");
        hsvDownsamplingProgramTexCoordinatesHandle = GLES20.glGetAttribLocation(hsvDownsamplingProgram, "texCoordinates");
        hsvDownsamplingProgramTextureHandle = GLES20.glGetUniformLocation(hsvDownsamplingProgram, "texture");
        hsvDownsamplingResSourceHandle = GLES20.glGetUniformLocation(hsvDownsamplingProgram, "resSource");
        hsvDownsamplingResTargetHandle = GLES20.glGetUniformLocation(hsvDownsamplingProgram, "resTarget");

        checkGLError("HSVAnalyzer: prepare");
    }

    @Override
    public void analyze(float[] camMatrix, RectF passepartout) {
        drawHSV(camMatrix, passepartout);
        for (int i = 0; i < nDownsampleSteps; i++) {
            drawHsvDownsampling(i, camMatrix);
        }

        int outW = wDownsampleStep[nDownsampleSteps -1];
        int outH = hDownsampleStep[nDownsampleSteps -1];

        ByteBuffer resultBuffer = ByteBuffer.allocateDirect(outW * outH * 4).order(ByteOrder.nativeOrder());
        resultBuffer.rewind();

        GLES20.glReadPixels(0, 0, outW, outH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, resultBuffer);
        resultBuffer.rewind();

        if (mode == Mode.hue) {
            long x = 0;
            long y = 0;
            while (resultBuffer.hasRemaining()) {
                long r = resultBuffer.get() & 0xff;
                long g = resultBuffer.get() & 0xff;
                long b = resultBuffer.get() & 0xff;
                long a = resultBuffer.get() & 0xff;
                y += ((r << 8) + g);
                x += ((b << 8) + a);
            }
            latestResult = Math.atan2(y, x) * RGB.HUE_MAX;
        } else {
            long sum = 0;
            long totalContribution = 0;
            while (resultBuffer.hasRemaining()) {
                long r = resultBuffer.get() & 0xff;
                long g = resultBuffer.get() & 0xff;
                long b = resultBuffer.get() & 0xff;
                long a = resultBuffer.get() & 0xff;
                sum += ((r << 8) + g);
                totalContribution += b;
            }
            latestResult = (double)sum / (double)(totalContribution*Math.pow(4, nDownsampleSteps));
        }

        checkGLError("hsv analyze");

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

    void drawHSV(float[] camMatrix, RectF passepartout) {
        makeCurrent(analyzingSurface, w, h);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor((int)Math.floor(w*(1.0-Math.max(passepartout.top, passepartout.bottom))), (int)Math.floor(h*(1.0-Math.max(passepartout.left, passepartout.right))), (int)Math.ceil(w*Math.abs(passepartout.height())), (int)Math.ceil(h*Math.abs(passepartout.width())));

        GLES20.glUseProgram(hsvProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(hsvProgramVerticesHandle);
        GLES20.glVertexAttribPointer(hsvProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(hsvProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(hsvProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glUniform1i(hsvProgramTextureHandle, 0);

        GLES20.glUniform2f(hsvProgramPassepartoutMinHandle, passepartout.left, passepartout.top);
        GLES20.glUniform2f(hsvProgramPassepartoutMaxHandle, passepartout.right, passepartout.bottom);

        GLES20.glUniformMatrix4fv(hsvProgramCamMatrixHandle, 1, false, camMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisableVertexAttribArray(hsvProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(hsvProgramTexCoordinatesHandle);

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        checkGLError("draw hsv");
    }

    void drawHsvDownsampling(int step, float[] camMatrix) {
        long start = System.nanoTime();
        makeCurrent(downsampleSurfaces[step], wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glUseProgram(hsvDownsamplingProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(hsvDownsamplingProgramVerticesHandle);
        GLES20.glVertexAttribPointer(hsvDownsamplingProgramVerticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(hsvDownsamplingProgramTexCoordinatesHandle);
        GLES20.glVertexAttribPointer(hsvDownsamplingProgramTexCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, downsamplingTextures[step]);
        EGL14.eglBindTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glUniform1i(hsvDownsamplingProgramTextureHandle, 0);

        GLES20.glUniform2f(hsvDownsamplingResSourceHandle, step == 0 ? w : wDownsampleStep[step-1], step == 0 ? h : hDownsampleStep[step-1]);
        GLES20.glUniform2f(hsvDownsamplingResTargetHandle, wDownsampleStep[step], hDownsampleStep[step]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        EGL14.eglReleaseTexImage(eglDisplay, (step == 0) ? analyzingSurface : downsampleSurfaces[step-1], EGL14.EGL_BACK_BUFFER);
        GLES20.glDisableVertexAttribArray(hsvDownsamplingProgramVerticesHandle);
        GLES20.glDisableVertexAttribArray(hsvDownsamplingProgramTexCoordinatesHandle);

        checkGLError("downsample hsv");
    }
}
