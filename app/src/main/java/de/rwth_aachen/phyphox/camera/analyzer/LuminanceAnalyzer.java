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
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class LuminanceAnalyzer extends AnalyzingModule {

    final static String luminanceFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;" +
            "uniform samplerExternalOES texture;" +
            "varying vec2 positionInPassepartout;" +
            "varying vec2 texPosition;" +

            "float linearize(float x) {" +
            "  if (x < 0.04045) " +
            "    return x/12.92;" +
            "  else" +
            "    return pow((x+0.055)/1.055, 2.4);" +
            "}" +

            "void main () {" +
            "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
            "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);" +
            "  } else {" +
            "    vec3 gammaRGB = texture2D(texture, texPosition).rgb;" +

//            "    vec3 linRGB = pow(gammaRGB, vec3(2.2, 2.2, 2.2));" +   //Adobe RGB or approximation of sRGB

            "    vec3 linRGB = vec3(linearize(gammaRGB.r), linearize(gammaRGB.g), linearize(gammaRGB.b));" +
            "    gl_FragColor = vec4(0.0, dot(linRGB, vec3(0.2126, 0.7152, 0.0722)), 1.0, 1.0);" +
            "  }" +
            "}";

    final static String lumaFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision highp float;" +
                    "uniform samplerExternalOES texture;" +
                    "varying vec2 positionInPassepartout;" +
                    "varying vec2 texPosition;" +
                    "void main () {" +
                    "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0)))) {" +
                    "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);" +
                    "  } else {" +
                    "    vec3 gammaRGB = texture2D(texture, texPosition).rgb;" +
                    "    gl_FragColor = vec4(0.0, dot(gammaRGB, vec3(0.2126, 0.7152, 0.0722)), 1.0, 1.0);" +
                    " }" +
                    "}";

    final static String luminanceDownsamplingFragmentShader =
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

    boolean linear = false;
    DataBuffer out;
    int luminanceProgram, luminanceDownsamplingProgram;
    int luminanceProgramVerticesHandle, luminanceProgramTexCoordinatesHandle, luminanceProgramCamMatrixHandle, luminanceProgramTextureHandle, luminanceProgramPassepartoutMinHandle, luminanceProgramPassepartoutMaxHandle;
    int luminanceDownsamplingProgramVerticesHandle, luminanceDownsamplingProgramTexCoordinatesHandle, luminanceDownsamplingProgramTextureHandle;
    int luminanceDownsamplingResSourceHandle, luminanceDownsamplingResTargetHandle;

    double latestResult = Double.NaN;
    public LuminanceAnalyzer(DataBuffer out, boolean linear) {
        this.linear = linear;
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

        luminanceDownsamplingProgram = buildProgram(interpolatingFullScreenVertexShader, luminanceDownsamplingFragmentShader);
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

        long luminance = 0;
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
            luminance += ((r << 8) + g);
            totalContribution += b;
        }

        checkGLError("luminance analyze");

        latestResult = (double)luminance / (double)(totalContribution*Math.pow(4, nDownsampleSteps));
    }

    @Override
    public void writeToBuffers(CameraSettingState state) {
        double exposureFactor = linear ? Math.pow(2.0, state.getCurrentApertureValue())/2.0 * 100.0/state.getCurrentIsoValue() * (1.0e9/60.0) / state.getCurrentShutterValue() : 1.0;
        out.append(latestResult*exposureFactor);
    }

    public void makeCurrent(EGLSurface eglSurface, int w, int h) {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("Camera preview: eglMakeCurrent failed");
        }
        GLES20.glViewport(0,0, w, h);
    }

    void drawLuminance(float[] camMatrix, RectF passepartout) {
        makeCurrent(analyzingSurface, w, h);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor((int)Math.floor(w*(1.0-Math.max(passepartout.top, passepartout.bottom))), (int)Math.floor(h*(1.0-Math.max(passepartout.left, passepartout.right))), (int)Math.ceil(w*Math.abs(passepartout.height())), (int)Math.ceil(h*Math.abs(passepartout.width())));

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

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        checkGLError("draw luminance");
    }

    void drawLuminanceDownsampling(int step, float[] camMatrix) {
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

        checkGLError("downsample luminance");
    }
}
