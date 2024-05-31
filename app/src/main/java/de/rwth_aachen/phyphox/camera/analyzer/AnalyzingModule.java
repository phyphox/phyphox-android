package de.rwth_aachen.phyphox.camera.analyzer;

import android.graphics.RectF;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;

import androidx.annotation.RequiresApi;

import de.rwth_aachen.phyphox.camera.model.CameraSettingState;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public abstract class AnalyzingModule {
    static int w, h;
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

    static EGLSurface downsampleSurfaces[] = new EGLSurface[nDownsampleSteps];
    static int wDownsampleStep[] = new int[nDownsampleSteps];
    static int hDownsampleStep[] = new int[nDownsampleSteps];

    static int downsamplingTextures[] = new int[nDownsampleSteps];

    static public void init(int w, int h, EGLContext eglContext, EGLDisplay eglDisplay, EGLConfig eglConfig, int cameraTexture) {
        AnalyzingModule.w = w;
        AnalyzingModule.h = h;
        AnalyzingModule.eglContext = eglContext;
        AnalyzingModule.eglConfig = eglConfig;
        AnalyzingModule.eglDisplay = eglDisplay;
        AnalyzingModule.cameraTexture = cameraTexture;

        int[] surfaceAttr = {
                EGL14.EGL_WIDTH, w,
                EGL14.EGL_HEIGHT, h,
                EGL14.EGL_TEXTURE_FORMAT, EGL14.EGL_TEXTURE_RGBA,
                EGL14.EGL_TEXTURE_TARGET, EGL14.EGL_TEXTURE_2D,
                EGL14.EGL_MIPMAP_TEXTURE, EGL14.EGL_FALSE,
                EGL14.EGL_NONE
        };
        analyzingSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttr, 0);

        GLES20.glGenTextures(nDownsampleSteps, downsamplingTextures, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        for (int i = 0; i < nDownsampleSteps; i++) {
            wDownsampleStep[i] = (i == 0) ? ((w + 3) / 4) : ((wDownsampleStep[i-1] + 3) / 4);
            hDownsampleStep[i] = (i == 0) ? ((h + 3) / 4) : ((hDownsampleStep[i-1] + 3) / 4);

            int[] surfaceDownsampleAttr = {
                    EGL14.EGL_WIDTH, wDownsampleStep[i],
                    EGL14.EGL_HEIGHT, hDownsampleStep[i],
                    EGL14.EGL_TEXTURE_FORMAT, EGL14.EGL_TEXTURE_RGBA,
                    EGL14.EGL_TEXTURE_TARGET, EGL14.EGL_TEXTURE_2D,
                    EGL14.EGL_NONE
            };
            downsampleSurfaces[i] = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceDownsampleAttr, 0);
        }
    }

    static public void release() {
        EGL14.eglDestroySurface(eglDisplay, analyzingSurface);
        for (int i = 0; i < nDownsampleSteps; i++) {
            EGL14.eglDestroySurface(eglDisplay, downsampleSurfaces[i]);
        }
    }

    public abstract void prepare();
    public abstract void analyze(float[] camMatrix, RectF passepartout);
    public abstract void writeToBuffers(CameraSettingState state);
    public void destroy() {

    }


}
