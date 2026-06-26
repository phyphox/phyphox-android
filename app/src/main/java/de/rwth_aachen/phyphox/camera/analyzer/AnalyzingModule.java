package de.rwth_aachen.phyphox.camera.analyzer;

import android.graphics.RectF;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

public abstract class AnalyzingModule {

    //Important: Many resources and references here are static as they either have to be shared
    // between the various AnalyzingModule instances or because we can save resources by doing so

    static int width, height;
    static EGLDisplay eglDisplay;
    static EGLContext eglContext;
    static EGLConfig eglConfig;
    static int cameraTexture;
    static EGLSurface analyzingSurface = null;
    static final int nDownsampleSteps = 3; // Must be <= 4 (analyzing modules are designed with this limit in mind)
    // 3 downsampling steps seem to be a good trade-off between fixed costs of each step and reducing CPU load.
    // However, this was only tested on a Nexus 5x which had its optimum at 3 and a Pixel 6 where 3 and 4 steps were
    // nearly indistinguishable. Note, that this probably also heavily depends on the resolution that the video
    // stream gets on this device. Both devices used a 1600x1200 stream, but older devices with lower resolutions
    // might reduce the preview stream resolution, hopefully evening out the lower performance of such devices.

    static EGLSurface[] downsampleSurfaces = new EGLSurface[nDownsampleSteps];
    static int[] wDownsampleStep = new int[nDownsampleSteps];
    static int[] hDownsampleStep = new int[nDownsampleSteps];

    static int[] downsamplingTextures = new int[nDownsampleSteps];

    public AnalyzingModule(){}

    static protected void init(int width, int height, EGLContext eglContext, EGLDisplay eglDisplay, EGLConfig eglConfig, int cameraTexture) {
        AnalyzingModule.width = width;
        AnalyzingModule.height = height;
        AnalyzingModule.eglContext = eglContext;
        AnalyzingModule.eglConfig = eglConfig;
        AnalyzingModule.eglDisplay = eglDisplay;
        AnalyzingModule.cameraTexture = cameraTexture;

        analyzingSurface = createPbufferSurface(width, height);
    }

    protected static void photometrySetupGL() {
        downsampleSurfaces = new EGLSurface[nDownsampleSteps];
        wDownsampleStep = new int[nDownsampleSteps];
        hDownsampleStep = new int[nDownsampleSteps];
        downsamplingTextures = new int[nDownsampleSteps];

        GLES20.glGenTextures(nDownsampleSteps, downsamplingTextures, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        for (int i = 0; i < nDownsampleSteps; i++) {
            int prevW = (i == 0) ? width : wDownsampleStep[i - 1];
            int prevH = (i == 0) ? height : hDownsampleStep[i - 1];

            wDownsampleStep[i] = (prevW + 3) / 4;
            hDownsampleStep[i] = (prevH + 3) / 4;

            downsampleSurfaces[i] = createPbufferSurface( wDownsampleStep[i], hDownsampleStep[i]);
        }

    }


     protected static void release() {
         if (analyzingSurface != null) {
             EGL14.eglDestroySurface(eglDisplay, analyzingSurface);
         }
         if (downsampleSurfaces != null) {
             for (EGLSurface surface : downsampleSurfaces) {
                 if (surface != null) EGL14.eglDestroySurface(eglDisplay, surface);
             }
         }
         if (downsamplingTextures != null) {
             GLES20.glDeleteTextures(nDownsampleSteps, downsamplingTextures, 0);
         }
    }

    protected static EGLSurface createPbufferSurface(int w, int h) {
        int[] surfaceAttr = {
                EGL14.EGL_WIDTH, w,
                EGL14.EGL_HEIGHT, h,
                EGL14.EGL_TEXTURE_FORMAT, EGL14.EGL_TEXTURE_RGBA,
                EGL14.EGL_TEXTURE_TARGET, EGL14.EGL_TEXTURE_2D,
                EGL14.EGL_MIPMAP_TEXTURE, EGL14.EGL_FALSE,
                EGL14.EGL_NONE
        };
        return EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttr, 0);
    }

    public abstract void prepare();
    public abstract void analyze(float[] camMatrix, RectF passepartout);
    public abstract void writeToBuffers(CameraSettingState state);
    public void destroy() {

    }


}
