package de.rwth_aachen.phyphox.camera.analyzer;
import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.buildProgram;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.checkGLError;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVertexShader;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboVertices;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.fullScreenVboTexCoordinates;

import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.Executor;

import android.opengl.EGLContext;

import javax.microedition.khronos.egl.EGL10;

import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AnalyzingOpenGLRendererPreviewOutput implements TextureView.SurfaceTextureListener {
    static Executor executor;

    final static String fragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;" +
            "uniform samplerExternalOES texture;" +
            "varying vec2 positionInPassepartout;" +
            "varying vec2 texPosition;" +
            "void main () {" +
            "  if (any(lessThan(positionInPassepartout, vec2(0.0, 0.0))) || any(greaterThan(positionInPassepartout, vec2(1.0, 1.0))))" +
            "       gl_FragColor = vec4(0.5, 0.5, 0.5, 1.0) * texture2D(texture, texPosition);" +
            "  else " +
            "       gl_FragColor = texture2D(texture, texPosition);" +
            "}";
    static int program, verticesHandle, texCoordinatesHandle, camMatrixHandle, textureHandle, passepartoutMinHandle, passepartoutMaxHandle;
    static EGLContext eglContext = null;
    static EGLDisplay eglDisplay;
    static EGLConfig eglConfig;
    static int cameraTexture;

    WeakReference<CameraPreviewScreen> cameraPreviewScreen;
    EGLSurface eglSurface;
    private SurfaceTexture surfaceTexture = null;
    private SurfaceTexture newSurface = null;
    int w, h;

    int[] surfaceAttribs = {
            EGL10.EGL_NONE
    };

    AnalyzingOpenGLRendererPreviewOutput(CameraPreviewScreen cameraPreviewScreen) {
        this.cameraPreviewScreen = new WeakReference<>(cameraPreviewScreen);
    }

    public boolean isGone() {
        return  cameraPreviewScreen.get() == null;
    }
    boolean makeCurrent() {
        if (eglContext == null)
            return false;
        if (newSurface != null) {
            if (surfaceTexture != null) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(eglDisplay, eglSurface);
            }

            surfaceTexture = newSurface;
            newSurface = null;

            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, surfaceAttribs, 0);
            if (eglSurface == null) {
                throw new RuntimeException("Camera Preview: Surface was null");
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw new RuntimeException("Camera preview: eglMakeCurrent failed");
            }
        }

        if (eglSurface == null)
            return false;

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("Camera preview: eglMakeCurrent failed");
        }
        GLES20.glViewport(0,0, w, h);
        return true;
    }

    static void prepareOpenGL(EGLContext eglContext, EGLDisplay eglDisplay, EGLConfig eglConfig, int eglCameraTexture) {
        AnalyzingOpenGLRendererPreviewOutput.eglContext = eglContext;
        AnalyzingOpenGLRendererPreviewOutput.eglDisplay = eglDisplay;
        AnalyzingOpenGLRendererPreviewOutput.eglConfig = eglConfig;
        AnalyzingOpenGLRendererPreviewOutput.cameraTexture = eglCameraTexture;

        program = buildProgram(fullScreenVertexShader, fragmentShader);

        verticesHandle = GLES20.glGetAttribLocation(program, "vertices");
        texCoordinatesHandle = GLES20.glGetAttribLocation(program, "texCoordinates");
        camMatrixHandle = GLES20.glGetUniformLocation(program, "camMatrix");
        textureHandle = GLES20.glGetUniformLocation(program, "texture");
        passepartoutMinHandle = GLES20.glGetUniformLocation(program, "passepartoutMin");
        passepartoutMaxHandle = GLES20.glGetUniformLocation(program, "passepartoutMax");

        checkGLError("preview: prepareOpenGL");
    }

    void draw(float[] camMatrix, RectF passepartout) {
        if (!makeCurrent())
            return;

        if (eglContext == null || surfaceTexture == null || isGone())
            return;

        GLES20.glUseProgram(program);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboVertices);
        GLES20.glEnableVertexAttribArray(verticesHandle);
        GLES20.glVertexAttribPointer(verticesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullScreenVboTexCoordinates);
        GLES20.glEnableVertexAttribArray(texCoordinatesHandle);
        GLES20.glVertexAttribPointer(texCoordinatesHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glUniform1i(textureHandle, 0);
        GLES20.glUniform2f(passepartoutMinHandle, passepartout.left, passepartout.top);
        GLES20.glUniform2f(passepartoutMaxHandle, passepartout.right, passepartout.bottom);

        GLES20.glUniformMatrix4fv(camMatrixHandle, 1, false, camMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        EGL14.eglSwapBuffers(eglDisplay, eglSurface);

        GLES20.glDisableVertexAttribArray(verticesHandle);
        GLES20.glDisableVertexAttribArray(texCoordinatesHandle);

        checkGLError("draw preview");
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        newSurface = st;
        this.w = width;
        this.h = height;
        cameraPreviewScreen.get().updateTransformation(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        newSurface = st;
        this.w = width;
        this.h = height;
        cameraPreviewScreen.get().updateTransformation(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        surfaceTexture = null;
        executor.execute(
                () -> {
                    if (eglSurface != null) {
                        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                        EGL14.eglDestroySurface(eglDisplay, eglSurface);
                        eglSurface = null;
                        checkGLError("destroySurface");
                    }
                }
        );
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {

    }

}
