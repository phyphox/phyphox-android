package de.rwth_aachen.phyphox.camera.analyzer;
import static de.rwth_aachen.phyphox.camera.analyzer.OpenGLHelper.checkGLError;

import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.ExperimentTimeReference;
import de.rwth_aachen.phyphox.Helper.RGB;
import de.rwth_aachen.phyphox.camera.CameraInput;
import de.rwth_aachen.phyphox.camera.model.CameraSettingState;
import de.rwth_aachen.phyphox.camera.ui.CameraPreviewScreen;
import kotlinx.coroutines.flow.StateFlow;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AnalyzingOpenGLRenderer implements Preview.SurfaceProvider, SurfaceTexture.OnFrameAvailableListener {

    public interface ExposureStatisticsListener {
        void newExposureStatistics(double minRGB, double maxRGB, double meanLuma);
    }

    StateFlow<CameraSettingState> cameraSettingValueState;
    Executor executor = Executors.newSingleThreadExecutor();
    public int previewWidth = 0;
    public int previewHeight = 0;
    EGLDisplay eglDisplay = null;
    EGLContext eglContext = null;
    EGLConfig eglConfig = null;
    int eglCameraTexture = -1;
    SurfaceTexture cameraSurfaceTexture = null;
    Surface cameraSurface = null;

    ExposureStatisticsListener exposureStatisticsListener;

    Lock dataLock;
    List<AnalyzingModule> analyzingModules = new ArrayList<>();
    ExposureAnalyzer exposureAnalyzer;
    Deque<AnalyzingOpenGLRendererPreviewOutput> previewOutputs = new ConcurrentLinkedDeque<>();

    public boolean measuring = false;
    ExperimentTimeReference experimentTimeReference;
    DataBuffer timeOutput;
    DataBuffer shutterSpeedOutput, apertureOutput, isoOutput;

    public AnalyzingOpenGLRenderer(CameraInput cameraInput, Lock lock, StateFlow<CameraSettingState> cameraSettingValueState, ExposureStatisticsListener exposureStatisticsListener) {
        this.cameraSettingValueState = cameraSettingValueState;
        this.experimentTimeReference = cameraInput.experimentTimeReference;

        this.timeOutput = cameraInput.getDataT();

        this.shutterSpeedOutput = cameraInput.getShutterSpeedDataBuffer();
        this.apertureOutput = cameraInput.getApertureDataBuffer();
        this.isoOutput = cameraInput.getIsoDataBuffer();

        this.dataLock = lock;
        if (cameraInput.getDataLuminance() != null) {
            analyzingModules.add(new LuminanceAnalyzer(cameraInput.getDataLuminance(), true));
        }
        if (cameraInput.getDataLuma() != null) {
            analyzingModules.add(new LuminanceAnalyzer(cameraInput.getDataLuma(), false));
        }
        if (cameraInput.getDataHue() != null) {
            analyzingModules.add(new HSVAnalyzer(cameraInput.getDataHue(), HSVAnalyzer.Mode.hue));
        }
        if (cameraInput.getDataSaturation() != null) {
            analyzingModules.add(new HSVAnalyzer(cameraInput.getDataSaturation(), HSVAnalyzer.Mode.saturation));
        }
        if (cameraInput.getDataValue() != null) {
            analyzingModules.add(new HSVAnalyzer(cameraInput.getDataValue(), HSVAnalyzer.Mode.value));
        }
        if (cameraInput.getDataThreshold() != null) {
            analyzingModules.add(new ThresholdAnalyzer(cameraInput.getDataThreshold(), cameraInput.getThresholdAnalyzerThreshold()));
        }

        this.exposureAnalyzer = new ExposureAnalyzer();
        this.exposureStatisticsListener = exposureStatisticsListener;

        AnalyzingOpenGLRendererPreviewOutput.executor = executor;
    }

    void createContext() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1);

        int[] configAttr = {
                EGL14.EGL_COLOR_BUFFER_TYPE, EGL14.EGL_RGB_BUFFER,
                EGL14.EGL_LEVEL, 0,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_LUMINANCE_SIZE, 0,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_BUFFER_SIZE, 32,
                EGL14.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfig = new int[1];
        EGL14.eglChooseConfig(eglDisplay, configAttr, 0, configs, 0, 1, numConfig, 0);
        if (numConfig[0] == 0) {
            throw new RuntimeException("Could not create OpenGL context: No configuration found.");
        }
        eglConfig = configs[0];

        int[] ctxAttrib = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0);

        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext);

        checkGLError("createContext");
    }


    public void releaseCameraSurface(Runnable callback) {
        executor.execute(
                () -> {
                    if (cameraSurface != null) {
                        cameraSurface.release();
                        cameraSurface = null;
                    }
                    checkGLError("releaseCameraSurface");
                    callback.run();
                }
        );
    }

    public void prepareOpenGL(int w, int h) {
        if (eglContext == null)
            createContext();

        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        if (eglCameraTexture == -1) {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            eglCameraTexture = textures[0];

            OpenGLHelper.prepareFullScreenVertices();
        }

        checkGLError("prepareOpenGL");

        AnalyzingModule.init(w, h, eglContext, eglDisplay, eglConfig, eglCameraTexture);
        for (AnalyzingModule analyzingModule : analyzingModules) {
            analyzingModule.prepare();
        }

        exposureAnalyzer.prepare();

        checkGLError("prepareOpenGL (modules)");

        AnalyzingOpenGLRendererPreviewOutput.prepareOpenGL(eglContext, eglDisplay, eglConfig, eglCameraTexture);

        checkGLError("prepareOpenGL (preview)");
    }

    public void attachTexturePreviewView(CameraPreviewScreen cameraPreviewScreen) {
        //Check if TV is already attached
        TextureView attachingTV = cameraPreviewScreen.getPreviewTextureView();
        for (AnalyzingOpenGLRendererPreviewOutput previewOutput : previewOutputs) {
            TextureView thisTV = previewOutput.cameraPreviewScreen.get().getPreviewTextureView();
            if (thisTV == attachingTV) {
                return;
            }
        }

        AnalyzingOpenGLRendererPreviewOutput analyzingOpenGLRendererPreviewOutput = new AnalyzingOpenGLRendererPreviewOutput(cameraPreviewScreen);
        TextureView previewTextureView = cameraPreviewScreen.getPreviewTextureView();
        previewTextureView.setSurfaceTextureListener(analyzingOpenGLRendererPreviewOutput);
        if (previewTextureView.isAvailable())
            analyzingOpenGLRendererPreviewOutput.onSurfaceTextureAvailable(previewTextureView.getSurfaceTexture(), previewTextureView.getWidth(), previewTextureView.getHeight());
        previewOutputs.add(analyzingOpenGLRendererPreviewOutput);
    }

    public void detachTexturePreviewView(CameraPreviewScreen cameraPreviewScreen) {
        TextureView detachingTV = cameraPreviewScreen.getPreviewTextureView();
        for (AnalyzingOpenGLRendererPreviewOutput previewOutput : previewOutputs) {
            TextureView thisTV = previewOutput.cameraPreviewScreen.get().getPreviewTextureView();
            if (thisTV == detachingTV) {
                previewOutputs.remove(previewOutput);
            }
        }
    }

    long renderingTotal = 0;
    long nFrames = 0;

    void writeToBuffers(double t, CameraSettingState state) {
        for (AnalyzingModule analyzingModule : analyzingModules)
            analyzingModule.writeToBuffers(state);
        if (timeOutput != null)
            timeOutput.append(t);
        if (shutterSpeedOutput != null)
            shutterSpeedOutput.append(state.getCurrentShutterValue()/1.0e9);
        if (apertureOutput != null)
            apertureOutput.append(state.getCurrentApertureValue());
        if (isoOutput != null)
            isoOutput.append(state.getCurrentIsoValue());
    }

    void draw() {
        double t = experimentTimeReference.getExperimentTime();
        CameraSettingState state = cameraSettingValueState.getValue();
        executor.execute(
                () -> {
                    if (eglContext == null || cameraSurfaceTexture == null)
                        return;

                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, eglContext);

                    long start = System.nanoTime();

                    cameraSurfaceTexture.updateTexImage();

                    float[] camMatrix = new float[16];
                    cameraSurfaceTexture.getTransformMatrix(camMatrix);

                    RectF passepartout = state.getCameraPassepartout();

                    boolean dataNeedsToBeWrittenToBuffers = true;
                    if (measuring) {
                        for (AnalyzingModule analyzingModule : analyzingModules) {
                            analyzingModule.analyze(camMatrix, passepartout);
                        }

                        if (dataLock.tryLock()) { //First try to write to buffers. If they available at the moment, draw preview first
                            writeToBuffers(t, state);
                            dataNeedsToBeWrittenToBuffers = false;
                            dataLock.unlock();
                        }
                    } else
                        dataNeedsToBeWrittenToBuffers = false;

                    if (state.getAutoExposure()) {
                        exposureAnalyzer.analyze(camMatrix, passepartout);
                        if (exposureStatisticsListener != null)
                            exposureStatisticsListener.newExposureStatistics(exposureAnalyzer.minRGB, exposureAnalyzer.maxRGB, exposureAnalyzer.meanLuma);
                    } else
                        exposureAnalyzer.reset();

                    for (AnalyzingOpenGLRendererPreviewOutput previewOutput : previewOutputs) {
                        previewOutput.draw(camMatrix, passepartout);
                    }

                    if (dataNeedsToBeWrittenToBuffers) {
                        dataLock.lock();
                        writeToBuffers(t, state);
                        dataLock.unlock();
                    }

                    checkGLError("draw");

                    if (measuring) {
                        renderingTotal += System.nanoTime() - start;
                        nFrames++;
                    }

                }
        );
    }

    @Override
    public void onSurfaceRequested(@NonNull SurfaceRequest request) {
        previewWidth = request.getResolution().getWidth();
        previewHeight = request.getResolution().getHeight();

        executor.execute(
                () -> {
                    if (eglContext == null)
                        prepareOpenGL(previewWidth, previewHeight);

                    cameraSurfaceTexture = new SurfaceTexture(eglCameraTexture);
                    cameraSurfaceTexture.setDefaultBufferSize(previewWidth, previewHeight);
                    cameraSurfaceTexture.setOnFrameAvailableListener(this);
                    Surface cameraSurface = new Surface(cameraSurfaceTexture);
                    request.provideSurface(cameraSurface, executor, result -> {
                        releaseCameraSurface(()->{});
                        checkGLError("destroy camera surface");
                    });
                    checkGLError("onSurfaceRequested");
                }
        );
        for (AnalyzingOpenGLRendererPreviewOutput previewOutput : previewOutputs) {
            previewOutput.cameraPreviewScreen.get().updateTransformation(previewOutput.w, previewOutput.h);
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        draw();
    }


}
