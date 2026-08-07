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
    //All analysis passes render into framebuffer objects backed by ordinary textures, so the
    //result of one pass can be sampled by the next one. (This used to be done by rendering into
    //pbuffer surfaces and binding them via eglBindTexImage, but that render-to-texture mechanism
    //is an optional EGL feature that some drivers do not support - on those devices every
    //analysis draw failed and camera experiments produced no data. FBOs are core in GLES2.)
    //The context still needs some surface to be made current, which is all this tiny pbuffer does.
    static EGLSurface analyzingSurface = null;
    static int analyzingTexture = 0;
    static int analyzingFramebuffer = 0;
    static final int nDownsampleSteps = 3; // Must be <= 4 (analyzing modules are designed with this limit in mind)
    // 3 downsampling steps seem to be a good trade-off between fixed costs of each step and reducing CPU load.
    // However, this was only tested on a Nexus 5x which had its optimum at 3 and a Pixel 6 where 3 and 4 steps were
    // nearly indistinguishable. Note, that this probably also heavily depends on the resolution that the video
    // stream gets on this device. Both devices used a 1600x1200 stream, but older devices with lower resolutions
    // might reduce the preview stream resolution, hopefully evening out the lower performance of such devices.

    static int[] wDownsampleStep = new int[nDownsampleSteps];
    static int[] hDownsampleStep = new int[nDownsampleSteps];

    static int[] downsamplingTextures = new int[nDownsampleSteps];
    static int[] downsamplingFramebuffers = new int[nDownsampleSteps];

    public AnalyzingModule(){}

    static protected void init(int width, int height, EGLContext eglContext, EGLDisplay eglDisplay, EGLConfig eglConfig, int cameraTexture) {
        AnalyzingModule.width = width;
        AnalyzingModule.height = height;
        AnalyzingModule.eglContext = eglContext;
        AnalyzingModule.eglConfig = eglConfig;
        AnalyzingModule.eglDisplay = eglDisplay;
        AnalyzingModule.cameraTexture = cameraTexture;

        analyzingSurface = createPbufferSurface(1, 1);
        makeCurrent(0, 1, 1);

        analyzingTexture = createRenderTexture(width, height);
        analyzingFramebuffer = createFramebuffer(analyzingTexture);
    }

    protected static void photometrySetupGL() {
        wDownsampleStep = new int[nDownsampleSteps];
        hDownsampleStep = new int[nDownsampleSteps];
        downsamplingTextures = new int[nDownsampleSteps];
        downsamplingFramebuffers = new int[nDownsampleSteps];

        for (int i = 0; i < nDownsampleSteps; i++) {
            int prevW = (i == 0) ? width : wDownsampleStep[i - 1];
            int prevH = (i == 0) ? height : hDownsampleStep[i - 1];

            wDownsampleStep[i] = (prevW + 3) / 4;
            hDownsampleStep[i] = (prevH + 3) / 4;

            downsamplingTextures[i] = createRenderTexture(wDownsampleStep[i], hDownsampleStep[i]);
            downsamplingFramebuffers[i] = createFramebuffer(downsamplingTextures[i]);
        }

    }


     protected static void release() {
         if (analyzingFramebuffer != 0 || downsamplingFramebuffers[0] != 0) {
             GLES20.glDeleteFramebuffers(1, new int[]{analyzingFramebuffer}, 0);
             GLES20.glDeleteTextures(1, new int[]{analyzingTexture}, 0);
             GLES20.glDeleteFramebuffers(nDownsampleSteps, downsamplingFramebuffers, 0);
             GLES20.glDeleteTextures(nDownsampleSteps, downsamplingTextures, 0);
             analyzingFramebuffer = 0;
             analyzingTexture = 0;
         }
         if (analyzingSurface != null) {
             EGL14.eglDestroySurface(eglDisplay, analyzingSurface);
             analyzingSurface = null;
         }
    }

    protected static EGLSurface createPbufferSurface(int w, int h) {
        int[] surfaceAttr = {
                EGL14.EGL_WIDTH, w,
                EGL14.EGL_HEIGHT, h,
                EGL14.EGL_NONE
        };
        return EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttr, 0);
    }

    protected static int createRenderTexture(int w, int h) {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return texture[0];
    }

    protected static int createFramebuffer(int texture) {
        int[] framebuffer = new int[1];
        GLES20.glGenFramebuffers(1, framebuffer, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            android.util.Log.e("AnalyzingModule", "Analysis framebuffer incomplete: 0x" + Integer.toHexString(status));
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        return framebuffer[0];
    }

    //Make the shared context current and direct rendering into the given framebuffer object
    //(0 meaning the pbuffer surface itself, which is only used during setup).
    protected static void makeCurrent(int framebuffer, int w, int h) {
        if (!EGL14.eglMakeCurrent(eglDisplay, analyzingSurface, analyzingSurface, eglContext)) {
            throw new RuntimeException("Camera analysis: eglMakeCurrent failed");
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glViewport(0,0, w, h);
    }

    public abstract void prepare();
    public abstract void analyze(float[] camMatrix, RectF passepartout);
    public abstract void writeToBuffers(CameraSettingState state);
    public void destroy() {

    }


}
