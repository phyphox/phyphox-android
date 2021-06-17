package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Vector;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class PlotAreaView extends TextureView {

    private void init() {

    }

    public PlotAreaView(Context context) {
        super(context);
        init();
    }


    public PlotAreaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

}

class CurveData {
    int vboX, vboY;
    int ibo;
    int ibCount, ibUsedCount;
    transient IntBuffer ib;

    int mapWidth;
    int n;
    GraphView.Style style;
    float color[] = new float[4];
    transient FloatBufferRepresentation fbX, fbY;
    transient List<ExperimentTimeReferenceSet> timeReferencesX, timeReferencesY;
}

class GraphSetup {
    private final Object lock = new Object();

    public boolean incrementalX = false;

    int plotBoundL, plotBoundT, plotBoundW, plotBoundH;
    int zaBoundL, zaBoundT, zaBoundW, zaBoundH;
    float minX, maxX, minY, maxY, minZ, maxZ;
    public final Vector<CurveData> dataSets = new Vector<>();
    public float[] positionMatrix = new float[16];
    public float[] zScaleMatrix = new float[16];
    public Vector<Integer> color = new Vector<>();
    public int historyLength = 1;
    public Vector<GraphView.Style> style = new Vector<>();
    public Vector<Float> lineWidth = new Vector<>();
    public boolean logX = false;
    public boolean logY = false;
    public boolean logZ = false;

    public Vector<Integer> colorScale = new Vector<>();

    public double[] xTics = null;
    public double[] yTics = null;
    public double[] zTics = null;

    public List<Double> trStarts = null;
    public List<Double> trStops = null;
    public List<Double> systemTimeReferenceGap = null;
    public boolean timeOnX = false;
    public boolean timeOnY = false;
    public boolean absoluteTime = false;
    public boolean linearTime = false;

    GraphSetup() {
        plotBoundL = 0;
        plotBoundT = 0;
        plotBoundW = 0;
        plotBoundH = 0;
        zaBoundL = 0;
        zaBoundT = 0;
        zaBoundW = 0;
        zaBoundH = 0;
        Matrix.setIdentityM(positionMatrix, 0);
        Matrix.setIdentityM(zScaleMatrix, 0);

        colorScale.add(0xff000000);
        colorScale.add(0xffff7e22);
        colorScale.add(0xffffffff);
    }

    public void initSize(int n) {
        color.setSize(n);
        style.setSize(n);
        lineWidth.setSize(n);
        for (int i = 0; i < n; i++) {
            color.set(i, 0xffffff);
            style.set(i, GraphView.Style.lines);
            lineWidth.set(i, 2.0f);
        }
    }

    public void setPlotBounds(float l, float t, float w, float h) {
        plotBoundL = Math.round(l);
        plotBoundT = Math.round(t);
        plotBoundW = Math.round(w);
        plotBoundH = Math.round(h);
    }

    public void setZAxisBounds(float l, float t, float w, float h) {
        zaBoundL = Math.round(l);
        zaBoundT = Math.round(t);
        zaBoundW = Math.round(w);
        zaBoundH = Math.round(h);
    }

    public void setTics(double[] xTics, double[] yTics, double[] zTics, PlotRenderer plotRenderer) {
        this.xTics = xTics;
        this.yTics = yTics;
        this.zTics = zTics;
        plotRenderer.notifyUpdateGrid();
    }

    public void setTimeRanges(List<Double> starts, List<Double> stops, List<Double> systemTimeReferenceGap, PlotRenderer plotRenderer) {
        this.trStarts = starts;
        this.trStops = stops;
        this.systemTimeReferenceGap = systemTimeReferenceGap;
        plotRenderer.notifyUpdateTimeRanges();
    }

    public void setDataBounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public void updateMatrix(float w, float h) {
        if (maxX == minX || maxY == minY || plotBoundW == 0 || plotBoundH == 0)
            return;
        float l, r, t, b;

        if (logX) {
            float logMinX = (float)Math.log(minX);
            float logMaxX = (float)Math.log(maxX);
            l = logMinX - plotBoundL / (float) plotBoundW * (logMaxX - logMinX);
            r = logMaxX + (w - plotBoundW - plotBoundL) / (float) plotBoundW * (logMaxX - logMinX);
        } else {
            l = minX - plotBoundL / (float) plotBoundW * (maxX - minX);
            r = maxX + (w - plotBoundW - plotBoundL) / (float) plotBoundW * (maxX - minX);
        }
        if (logY) {
            float logMinY = (float)Math.log(minY);
            float logMaxY = (float)Math.log(maxY);
            b = logMinY - (h - plotBoundT - plotBoundH) / (float) plotBoundH * (logMaxY - logMinY);
            t = logMaxY + (plotBoundT) / (float) plotBoundH * (logMaxY - logMinY);
        } else {
            b = minY - (h - plotBoundT - plotBoundH) / (float) plotBoundH * (maxY - minY);
            t = maxY + (plotBoundT) / (float) plotBoundH * (maxY - minY);
        }

        if (style.contains(GraphView.Style.mapXY) && maxZ != minZ) {
            float zminOnX, zmaxOnX, zmin, zmax;
            if (logZ) {
                zmin = (float) Math.log(minZ);
                zmax = (float) Math.log(maxZ);
            } else {
                zmin = minZ;
                zmax = maxZ;
            }
            if (zmin == zmax) {
                zmin -= 1.0;
                zmax += 1.0;
            }
            zminOnX = zmin - plotBoundL / (float) plotBoundW * (zmax - zmin);
            zmaxOnX = zmax + (w - plotBoundW - plotBoundL) / (float) plotBoundW * (zmax- zmin);

            Matrix.orthoM(positionMatrix, 0, l, r, b, t, -zmin, -zmax);
            Matrix.orthoM(zScaleMatrix, 0, zminOnX, zmaxOnX, 0, 1, -zmin, -zmax);
        } else
            Matrix.orthoM(positionMatrix, 0, l, r, b, t, -1, 1);
    }

    public void setData(FloatBufferRepresentation[] x, FloatBufferRepresentation[] y, List<ExperimentTimeReferenceSet>[] timeReferencesX, List<ExperimentTimeReferenceSet>[] timeReferencesY, int n, GraphView.Style[] style, int[] mapWidth, PlotRenderer plotRenderer) {
        for (int i = 0; i < n; i++) {
            if (dataSets.size() <= i) {
                CurveData newData = new CurveData();
                if (i == 0 || historyLength == 1) {
                    newData.color[0] = ((color.get(i) & 0xff0000) >> 16)/255.f;
                    newData.color[1] = ((color.get(i) & 0xff00) >> 8)/255.f;
                    newData.color[2] = (color.get(i) & 0xff)/255.f;
                    newData.color[3] = 1.f;
                } else {
                    newData.color[0] = 1.f;
                    newData.color[1] = 1.f;
                    newData.color[2] = 1.f;
                    newData.color[3] = 0.6f-(i+1)*0.6f/historyLength;
                }
                newData.vboX = 0;
                newData.vboY = 0;
                newData.ibo = 0;
                newData.ibCount = 0;
                newData.ibUsedCount = 0;
                newData.ib = null;
                newData.style = style[i];
                newData.mapWidth = mapWidth[i];
                dataSets.add(newData);
            }
            final CurveData data = dataSets.get(i);

            data.fbX = x[i];
            data.fbY = y[i];
            data.timeReferencesX = timeReferencesX[i];
            data.timeReferencesY = timeReferencesY[i];
        }

        plotRenderer.notifyUpdateBuffers();
    }
}

class PlotRenderer extends Thread implements TextureView.SurfaceTextureListener {
    private final Object lock = new Object();
    private SurfaceTexture surfaceTexture;
    private boolean done = false;
    private boolean readyToRender = false;
    private boolean renderRequested = false;
    private boolean updateBuffers = false;
    private boolean updateGrid = false;
    private boolean updateTimeRanges = false;
    private SurfaceTexture newSurface = null;

    private GraphSetup graphSetup = null;

    int h, w;
    int bgColor;

    private int glProgram, gridProgram, timeRangeProgram, mapProgram;
    private int positionXHandle, positionYHandle;
    private int mapPositionXHandle, mapPositionYHandle, mapPositionZHandle;
    private int colorHandle;
    private int positionMatrixHandle, mapPositionMatrixHandle;
    private int logXYHandle, mapLogXYZHandle, mapColorMapHandle;
    private int sizeHandle;
    private int offsetYHandle, offsetXHandle;

    private int gridPositionHandle;
    private int gridMatrixHandle;
    private int nGridLines = 0;
    private int nZGridLines = 0;
    int vboGrid = 0;
    int vboZGrid = 0;
    int vboZScaleX = 0;
    int vboZScaleY = 0;
    int vboZScaleZ = 0;
    int colorScaleTexture = 0;

    private int timeRangePositionHandle;
    private int timeRangeMatrixHandle;
    private int timeRangeAlphaHandle;
    private int nTimeRanges = 0;
    int vboTimeRanges = 0;

    EGL10 egl;
    EGLDisplay eglDisplay;
    EGLContext eglContext;
    EGLSurface eglSurface;

    EGLConfig favConfig = null;
    int[] surfaceAttribs = {
            EGL10.EGL_NONE
    };

    final String vertexShader =
            "uniform vec4 in_color;" +
            "uniform mat4 positionMatrix;" +

            "uniform int logXY;" +
            "uniform float size;" +

            "attribute float positionX;" +
            "attribute float positionY;" +

            "varying vec4 v_color;" +
            "varying float invalid;" +

            "float posX, posY;" +
            "uniform float offsetX;" +
            "uniform float offsetY;" +

            " bool isinvalid( float val ) {" +
            "    return val < -3.3e38;" + //see getFloatBuffer(...) in DataBuffer.java for explanation
            "}" +

            "void main () {" +
            "   if (isinvalid(positionX) || isinvalid(positionY)) {" + //There is a NaN or +/-Inf in any of the
            "       invalid = 1.0;" +                                  //Mark invalid for fragment shader (only works for line to this point if the value is interpolated (i.e. not on the HTC One X)
            "       gl_Position = vec4(0.0/0.0, -1.0/0.0, 1.0/0.0, 0.0/0.0);" +  //Break calculation by setting positions to NaN/Inf, skips rendering this line on most devices
            "       gl_PointSize = 0.0;" +                             //Set point size to zero to skip if all else fails (hopefully)
            "       return;" +
            "   } else" +
            "       invalid = 0.0;" +
            "   v_color = in_color;" +
            "   if (logXY == 1 || logXY == 3)" +
            "       posX = log(positionX + offsetX);" +
            "   else" +
            "       posX = positionX + offsetX;" +
            "   if (logXY >= 2)" +
            "       posY = log(positionY + offsetY);" +
            "   else" +
            "       posY = positionY + offsetY;" +
            "   gl_Position = positionMatrix * vec4(posX, posY, 0., 1.);" +
            "   gl_PointSize = size;" +
            "}";

    final String fragmentShader =
            "precision mediump float;" +
            "varying float invalid;" +
            "varying vec4 v_color;" +

            "void main () {" +
            "   if (invalid > 0.0)" +
            "       discard;" +
            "   gl_FragColor = v_color;" +
            "}";

    final String gridShader =
            "attribute vec2 position;" +
            "uniform mat4 positionMatrix;" +

            "vec2 pos;" +

            "void main () {" +
            "   pos = vec2(positionMatrix * vec4(position, 0., 1.));" +
            "   gl_Position = vec4(pos, 0., 1.);" +
            "}";

    final String gridFragmentShader =
            "precision mediump float;" +
            "void main () {" +
            "   gl_FragColor = vec4(1.0, 1.0, 1.0, 0.4);" +
            "}";

    final String timeRangeShader =
            "attribute vec2 position;" +
                    "uniform mat4 positionMatrix;" +

                    "vec2 pos;" +

                    "void main () {" +
                    "   pos = vec2(positionMatrix * vec4(position, 0., 1.));" +
                    "   gl_Position = vec4(pos, 0., 1.);" +
                    "}";

    final String timeRangeFragmentShader =
            "precision mediump float;" +
            "uniform float alpha;" +

            "void main () {" +
                    "   gl_FragColor = vec4(1.0, 0.0, 0.0, alpha);" +
                    "}";

    final String mapShader =
            "uniform mat4 positionMatrix;" +

            "uniform int logXYZ;" +

            "attribute float positionX;" +
            "attribute float positionY;" +
            "attribute float positionZ;" +

            "float posX, posY, posZ;" +
            "vec4 posRaw;" +

            "void main () {" +
            "   if (logXYZ == 1 || logXYZ == 3 || logXYZ == 5 || logXYZ == 7)" +
            "       posX = log(positionX);" +
            "   else" +
            "       posX = positionX;" +
            "   if (logXYZ == 2 || logXYZ == 3 || logXYZ == 6 || logXYZ == 7)" +
            "       posY = log(positionY);" +
            "   else" +
            "       posY = positionY;" +
            "   if (logXYZ == 4 || logXYZ == 5 || logXYZ == 6 || logXYZ == 7)" +
            "       posZ = log(positionZ);" +
            "   else" +
            "       posZ = positionZ;" +
            "   posRaw = positionMatrix * vec4(posX, posY, posZ, 1.);" +
            "   gl_Position = vec4(posRaw.xy, clamp(posRaw.z, -1.0, 1.0), posRaw.w);" +
            "}";

    final String mapFragmentShader =
            "precision mediump float;" +
            "uniform sampler2D colorMap;" +
            "void main () {" +
            "   gl_FragColor = vec4(texture2D(colorMap, vec2(gl_FragCoord.z,0.0)).rgb, 1.0);" +
            "}";

    PlotRenderer(Resources res) {
        super("PlotRenderer GL");
        bgColor = res.getColor(R.color.backgroundExp);
    }

    @Override
    public void run() {
        while (true) {
            SurfaceTexture lSurfaceTexture = null;

            synchronized (lock) {
                while (!done && (lSurfaceTexture = surfaceTexture) == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    }
                }
                if (done) {
                    break;
                }
                initGL();
            }

            if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }

            initScene();

            //Render and update at least once...
            renderRequested = true;
            updateBuffers = true;
            updateGrid = true;
            updateTimeRanges = true;

            while (true) {
                synchronized (lock) {
                    readyToRender = true;
                    while (!(renderRequested || done || ((lSurfaceTexture = surfaceTexture) == null))) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);
                        }
                    }
                    readyToRender = false;
                    if (done || (lSurfaceTexture = surfaceTexture) == null) {
                        break;
                    }
                    renderRequested = false;
                }

                synchronized (lock) {
                    if (newSurface != null) {

                        deinitScene();

                        egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                        egl.eglDestroySurface(eglDisplay, eglSurface);

                        egl.eglDestroyContext(eglDisplay, eglContext);

                        surfaceTexture = newSurface;
                        newSurface = null;

                        eglContext = egl.eglCreateContext(eglDisplay, favConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
                        if (eglContext == null || eglContext == EGL10.EGL_NO_CONTEXT) {
                            throw new RuntimeException("No eglContext");
                        }

                        eglSurface = egl.eglCreateWindowSurface(eglDisplay, favConfig, surfaceTexture, surfaceAttribs);
                        if (eglSurface == null) {
                            throw new RuntimeException("surface was null");
                        }

                        if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                            throw new RuntimeException("eglMakeCurrent failed");
                        }

                        initScene();

                    }
                }

                if (updateBuffers) {
                    doUpdateBuffers();
                    updateBuffers = false;
                }

                if (updateGrid) {
                    doUpdateGrid();
                    updateGrid = false;
                }

                if (updateTimeRanges) {
                    doUpdateTimeRanges();
                    updateTimeRanges = false;
                }

                drawFrame();

                synchronized (lock) {
                    if ((lSurfaceTexture = surfaceTexture) == null) {
                        break;
                    } else
                        egl.eglSwapBuffers(eglDisplay, eglSurface);
                }
            }

            deinitScene();
            deinitGL();
        }
    }

    private final int EGL_OPENGL_ES2_BIT = 4;
    private final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    private int[] attrib_list = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL10.EGL_NONE
    };

    public void initGL() {
        egl = (EGL10) EGLContext.getEGL();
        eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (!egl.eglInitialize(eglDisplay, null)) {
            throw new RuntimeException("Could not initialize EGL10");
        }

        int[] attribList = {
                EGL10.EGL_RED_SIZE, 4,
                EGL10.EGL_GREEN_SIZE, 4,
                EGL10.EGL_BLUE_SIZE, 4,
                EGL10.EGL_ALPHA_SIZE, 1,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT | EGL10.EGL_PBUFFER_BIT,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_NONE
        };
        int[] arg = new int[1];

        if (!egl.eglChooseConfig(eglDisplay, attribList, null, 0, arg)) {
            throw new RuntimeException("unable to retrieve config count");
        }

        int numConfigs = arg[0];
        if (numConfigs <= 0) {
            throw new RuntimeException("minimum OpenGL requirements could not be fulfilled.");
        }

        EGLConfig[] configs = new EGLConfig[numConfigs];

        if (!egl.eglChooseConfig(eglDisplay, attribList, configs, numConfigs, arg)) {
            throw new RuntimeException("unable to retrieve config list config");
        }

        int configHighScore = 0; //we will calculate a score based on number of colors, AA samples etc.
        favConfig = null; //Our favourite config will be remembered here...

        for (EGLConfig config : configs) {
            int[] value = new int[1];
            int r = egl.eglGetConfigAttrib(eglDisplay, config, EGL10.EGL_RED_SIZE, value) ? value[0] : 0;
            int g = egl.eglGetConfigAttrib(eglDisplay, config, EGL10.EGL_GREEN_SIZE, value) ? value[0] : 0;
            int b = egl.eglGetConfigAttrib(eglDisplay, config, EGL10.EGL_BLUE_SIZE, value) ? value[0] : 0;
            int a = egl.eglGetConfigAttrib(eglDisplay, config, EGL10.EGL_ALPHA_SIZE, value) ? value[0] : 0;
            int d = egl.eglGetConfigAttrib(eglDisplay, config, EGL10.EGL_DEPTH_SIZE, value) ? value[0] : 0;
            int s = egl.eglGetConfigAttrib(eglDisplay, config, EGL10.EGL_STENCIL_SIZE, value) ? value[0] : 0;
            int samples = egl.eglGetConfigAttrib(eglDisplay, config, EGL10.EGL_SAMPLES, value) ? value[0] : 0;
            int transp = egl.eglGetConfigAttrib(eglDisplay, config, EGL10.EGL_TRANSPARENT_TYPE, value) ? value[0] : 0;

            int score = 0;
            score += 10*(r + g + b); //We want as many bits as possible here...
            score += a;              //Alpha is not too important, but nice to have some precision here
            score -= d;              //We don't need a depth buffer
            score -= s;              //And we do not need a stencil buffer
            score += 5*samples;     //We love anti alias
            score += (transp == EGL10.EGL_NONE ? 3 : 0); //We prefer a non-transparent background

            if (score > configHighScore) {
                configHighScore = score;
                favConfig = config;
            }
        }

        if (favConfig == null) {
            throw new RuntimeException("No OpenGL config found. Should not happen here...");
        }

/*
        int[] value = new int[1];
        int r = egl.eglGetConfigAttrib(eglDisplay, favConfig, EGL10.EGL_RED_SIZE, value) ? value[0] : 0;
        int g = egl.eglGetConfigAttrib(eglDisplay, favConfig, EGL10.EGL_GREEN_SIZE, value) ? value[0] : 0;
        int b = egl.eglGetConfigAttrib(eglDisplay, favConfig, EGL10.EGL_BLUE_SIZE, value) ? value[0] : 0;
        int a = egl.eglGetConfigAttrib(eglDisplay, favConfig, EGL10.EGL_ALPHA_SIZE, value) ? value[0] : 0;
        int d = egl.eglGetConfigAttrib(eglDisplay, favConfig, EGL10.EGL_DEPTH_SIZE, value) ? value[0] : 0;
        int s = egl.eglGetConfigAttrib(eglDisplay, favConfig, EGL10.EGL_STENCIL_SIZE, value) ? value[0] : 0;
        int samples = egl.eglGetConfigAttrib(eglDisplay, favConfig, EGL10.EGL_SAMPLES, value) ? value[0] : 0;
        int transp = egl.eglGetConfigAttrib(eglDisplay, favConfig, EGL10.EGL_TRANSPARENT_TYPE, value) ? value[0] : 0;
        Log.d("initGL", "Mode selected from " + numConfigs + " available:\nr = " + r + ", g = " + g + ", b = " + b + ", a = " + a + "\nDepth = " + d + ", Stencil = " + s + "\nSamples = " + samples + ", Transparent type = " + transp);
*/
        eglContext = egl.eglCreateContext(eglDisplay, favConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
        if (eglContext == null || eglContext == EGL10.EGL_NO_CONTEXT) {
            throw new RuntimeException("No eglContext");
        }

        eglSurface = egl.eglCreateWindowSurface(eglDisplay, favConfig, surfaceTexture, surfaceAttribs);
        if (eglSurface == null) {
            throw new RuntimeException("surface was null");
        }

    }

    public void deinitGL() {
        if (surfaceTexture != null)
            surfaceTexture.release();
        egl.eglDestroySurface(eglDisplay, eglSurface);
        eglSurface = null;
        egl.eglDestroyContext(eglDisplay, eglContext);
        eglContext = null;
        eglDisplay = null;
        egl = null;
    }

    public static int loadShader(int type, String shaderCode){
        int shader = GLES20.glCreateShader(type);

        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.d("PlotRenderer", "Shader error\n" + GLES20.glGetShaderInfoLog(shader));
            return 0;
        }
        return shader;
    }

    public void initScene() {

        GLES20.glClearColor(((bgColor & 0xff0000) >> 16) / 255.f, ((bgColor & 0xff00) >> 8) / 255.f, (bgColor & 0xff) / 255.f, 1.0f);

        int iVertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        int iFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);
        glProgram = GLES20.glCreateProgram();

        GLES20.glAttachShader(glProgram, iVertexShader);
        GLES20.glAttachShader(glProgram, iFragmentShader);
        GLES20.glLinkProgram(glProgram);

        GLES20.glUseProgram(glProgram);

        positionXHandle = GLES20.glGetAttribLocation(glProgram, "positionX");
        positionYHandle = GLES20.glGetAttribLocation(glProgram, "positionY");

        positionMatrixHandle = GLES20.glGetUniformLocation(glProgram, "positionMatrix");
        colorHandle = GLES20.glGetUniformLocation(glProgram, "in_color");
        logXYHandle = GLES20.glGetUniformLocation(glProgram, "logXY");
        sizeHandle = GLES20.glGetUniformLocation(glProgram, "size");
        offsetXHandle = GLES20.glGetUniformLocation(glProgram, "offsetX");
        offsetYHandle = GLES20.glGetUniformLocation(glProgram, "offsetY");

        GLES20.glUseProgram(0);

        int iMapShader = loadShader(GLES20.GL_VERTEX_SHADER, mapShader);
        int iMapFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, mapFragmentShader);
        mapProgram = GLES20.glCreateProgram();

        GLES20.glAttachShader(mapProgram, iMapShader);
        GLES20.glAttachShader(mapProgram, iMapFragmentShader);
        GLES20.glLinkProgram(mapProgram);

        GLES20.glUseProgram(mapProgram);

        mapPositionXHandle = GLES20.glGetAttribLocation(mapProgram, "positionX");
        mapPositionYHandle = GLES20.glGetAttribLocation(mapProgram, "positionY");
        mapPositionZHandle = GLES20.glGetAttribLocation(mapProgram, "positionZ");

        mapPositionMatrixHandle = GLES20.glGetUniformLocation(mapProgram, "positionMatrix");
        mapLogXYZHandle = GLES20.glGetUniformLocation(mapProgram, "logXYZ");
        mapColorMapHandle = GLES20.glGetUniformLocation(mapProgram, "colorMap");

        GLES20.glUseProgram(0);

        int iGridShader = loadShader(GLES20.GL_VERTEX_SHADER, gridShader);
        int iGridFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, gridFragmentShader);
        gridProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(gridProgram, iGridShader);
        GLES20.glAttachShader(gridProgram, iGridFragmentShader);
        GLES20.glLinkProgram(gridProgram);

        GLES20.glUseProgram(gridProgram);

        gridPositionHandle = GLES20.glGetAttribLocation(gridProgram, "position");
        gridMatrixHandle = GLES20.glGetUniformLocation(gridProgram, "positionMatrix");

        int ref[] = new int[5];
        GLES20.glGenBuffers(5, ref, 0);
        vboGrid = ref[0];
        vboZGrid = ref[1];
        vboZScaleX = ref[2];
        vboZScaleY = ref[3];
        vboZScaleZ = ref[4];

        int texRef[] = new int[1];
        GLES20.glGenTextures ( 1, texRef, 0 );
        colorScaleTexture = texRef[0];

        GLES20.glUseProgram(0);

        int iTimeRangeShader = loadShader(GLES20.GL_VERTEX_SHADER, timeRangeShader);
        int iTimeRangeFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, timeRangeFragmentShader);
        timeRangeProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(timeRangeProgram, iTimeRangeShader);
        GLES20.glAttachShader(timeRangeProgram, iTimeRangeFragmentShader);
        GLES20.glLinkProgram(timeRangeProgram);

        GLES20.glUseProgram(timeRangeProgram);

        timeRangePositionHandle = GLES20.glGetAttribLocation(timeRangeProgram, "position");
        timeRangeMatrixHandle = GLES20.glGetUniformLocation(timeRangeProgram, "positionMatrix");

        timeRangeAlphaHandle = GLES20.glGetUniformLocation(timeRangeProgram, "alpha");

        GLES20.glGenBuffers(1, ref, 0);
        vboTimeRanges = ref[0];

        GLES20.glUseProgram(0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    public void deinitScene() {
    }

    private void drawGrid() {
        //Draw grid
        GLES20.glUseProgram(gridProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboGrid);
        GLES20.glEnableVertexAttribArray(gridPositionHandle);
        GLES20.glVertexAttribPointer(gridPositionHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glUniformMatrix4fv(gridMatrixHandle, 1, false, graphSetup.positionMatrix, 0);

        GLES20.glLineWidth(1.f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2*nGridLines);

        GLES20.glDisableVertexAttribArray(gridPositionHandle);

        if (graphSetup.style.contains(GraphView.Style.mapXY)) {

            GLES20.glScissor(graphSetup.zaBoundL+1, h-graphSetup.zaBoundH-graphSetup.zaBoundT-1, graphSetup.zaBoundW-2, graphSetup.zaBoundH-2);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboZGrid);
            GLES20.glEnableVertexAttribArray(gridPositionHandle);
            GLES20.glVertexAttribPointer(gridPositionHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

            GLES20.glUniformMatrix4fv(gridMatrixHandle, 1, false, graphSetup.zScaleMatrix, 0);
            GLES20.glLineWidth(1.f);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2 * nZGridLines);
            GLES20.glDisableVertexAttribArray(gridPositionHandle);

            GLES20.glScissor(graphSetup.plotBoundL+1, h-graphSetup.plotBoundH-graphSetup.plotBoundT-1, graphSetup.plotBoundW-2, graphSetup.plotBoundH-2);
        }
    }

    private void drawTimeRanges() {
        //Draw time ranges
        GLES20.glUseProgram(timeRangeProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboTimeRanges);
        GLES20.glEnableVertexAttribArray(timeRangePositionHandle);
        GLES20.glVertexAttribPointer(timeRangePositionHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glUniformMatrix4fv(timeRangeMatrixHandle, 1, false, graphSetup.positionMatrix, 0);

        GLES20.glLineWidth(1.f);

        for (int i = 0; i < nTimeRanges; i++) {
            GLES20.glUniform1f(timeRangeAlphaHandle, 0.2f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 4*i, 4);
            GLES20.glUniform1f(timeRangeAlphaHandle, 0.6f);
            GLES20.glDrawArrays(GLES20.GL_LINES, 4*i, 2);
            GLES20.glDrawArrays(GLES20.GL_LINES, 4*i+2, 2);
        }

        GLES20.glDisableVertexAttribArray(timeRangePositionHandle);
    }

    private void drawCurve(int i, int lastValidX, List<ExperimentTimeReferenceSet> lastValidXTimeReference) {

        GLES20.glUseProgram(glProgram);

        GLES20.glUniformMatrix4fv(positionMatrixHandle, 1, false, graphSetup.positionMatrix, 0);
        GLES20.glUniform1i(logXYHandle, (graphSetup.logX ? 0x01 : 0x00) | (graphSetup.logY ? 0x02 : 0x00));

        GLES20.glLineWidth(2.f * graphSetup.lineWidth.get(i));

        CurveData dataSet = graphSetup.dataSets.get(i);
        if (dataSet.n == 0 || (dataSet.n < 2 && !(graphSetup.style.get(i) == GraphView.Style.dots)))
            return;

        if (lastValidX == 0)
            return;

        GLES20.glEnableVertexAttribArray(positionXHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, lastValidX);
        GLES20.glVertexAttribPointer(positionXHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dataSet.vboY);
        GLES20.glEnableVertexAttribArray(positionYHandle);
        GLES20.glVertexAttribPointer(positionYHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glUniform4fv(colorHandle, 1, dataSet.color, 0);

        GLES20.glUniform1f(offsetXHandle, 0.0f);
        GLES20.glUniform1f(offsetYHandle, 0.0f);

        int geometry = GLES20.GL_LINE_STRIP;
        int verticesPerValue = 1; //Vertices per data point to align time reference data
        if (graphSetup.style.get(i) == GraphView.Style.dots) {
            GLES20.glUniform1f(sizeHandle, graphSetup.lineWidth.get(i) * 4.f);
            geometry = GLES20.GL_POINTS;
        } else if (graphSetup.style.get(i) == GraphView.Style.hbars || graphSetup.style.get(i) == GraphView.Style.vbars) {
            geometry = GLES20.GL_TRIANGLES;
            verticesPerValue = 6;
        }

        if (graphSetup.timeOnX && lastValidXTimeReference != null && lastValidXTimeReference.size() > 0) {
            for (ExperimentTimeReferenceSet timeReference : lastValidXTimeReference) {
                if (graphSetup.absoluteTime && !graphSetup.linearTime)
                    GLES20.glUniform1f(offsetXHandle, (float)((timeReference.systemTime - lastValidXTimeReference.get(0).systemTime)*0.001 - timeReference.experimentTime));
                else if (!graphSetup.absoluteTime && graphSetup.linearTime) {
                    if (timeReference.isPaused)
                        continue;
                    GLES20.glUniform1f(offsetXHandle, -(float)((timeReference.systemTime - lastValidXTimeReference.get(0).systemTime)*0.001 - timeReference.experimentTime));
                }
                GLES20.glDrawArrays(geometry, verticesPerValue*timeReference.index, Math.min(verticesPerValue*timeReference.count, dataSet.n-verticesPerValue*timeReference.index));
            }
        } else if (graphSetup.timeOnY && dataSet.timeReferencesY != null && dataSet.timeReferencesY.size() > 0) {
            for (ExperimentTimeReferenceSet timeReference : dataSet.timeReferencesY) {
                if (graphSetup.absoluteTime && !graphSetup.linearTime)
                    GLES20.glUniform1f(offsetYHandle, (float)((timeReference.systemTime - dataSet.timeReferencesY.get(0).systemTime)*0.001 - timeReference.experimentTime));
                else if (!graphSetup.absoluteTime && graphSetup.linearTime) {
                    if (timeReference.isPaused)
                        continue;
                    GLES20.glUniform1f(offsetYHandle, -(float)((timeReference.systemTime - dataSet.timeReferencesY.get(0).systemTime)*0.001 - timeReference.experimentTime));
                }

                GLES20.glDrawArrays(geometry, verticesPerValue*timeReference.index, Math.min(verticesPerValue*timeReference.count, dataSet.n-verticesPerValue*timeReference.index));
            }
        } else {
            GLES20.glDrawArrays(geometry, 0, dataSet.n);
        }

        GLES20.glDisableVertexAttribArray(positionXHandle);
        GLES20.glDisableVertexAttribArray(positionYHandle);
    }

    private void drawMap(int i) {

        //Draw map
        GLES20.glUseProgram(mapProgram);
        GLES20.glUniformMatrix4fv(mapPositionMatrixHandle, 1, false, graphSetup.positionMatrix, 0);
        GLES20.glUniform1i(mapLogXYZHandle, (graphSetup.logX ? 0x01 : 0x00) | (graphSetup.logY ? 0x02 : 0x00) | (graphSetup.logZ ? 0x04 : 0x00));

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorScaleTexture);
        GLES20.glUniform1i(mapColorMapHandle, 0);

        CurveData dataSet = graphSetup.dataSets.get(i);
        if (dataSet.n < 4)
            return;

        if (i+1 >= graphSetup.dataSets.size())
            return;
        CurveData dataSetZ = graphSetup.dataSets.get(i+1);
        if (dataSetZ.n < 4)
            return;
        if (dataSet.vboX == 0)
            return;
        if (dataSet.vboY == 0)
            return;
        if (dataSetZ.vboY == 0)
            return;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dataSet.vboX);
        GLES20.glEnableVertexAttribArray(mapPositionXHandle);
        GLES20.glVertexAttribPointer(mapPositionXHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dataSet.vboY);
        GLES20.glEnableVertexAttribArray(mapPositionYHandle);
        GLES20.glVertexAttribPointer(mapPositionYHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dataSetZ.vboY);
        GLES20.glEnableVertexAttribArray(mapPositionZHandle);
        GLES20.glVertexAttribPointer(mapPositionZHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

        if (dataSet.ibUsedCount < 4)
            return;

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, dataSet.ibo);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, dataSet.ibUsedCount, GLES20.GL_UNSIGNED_INT, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        //Draw scale

        GLES20.glScissor(graphSetup.zaBoundL+1, h-graphSetup.zaBoundH-graphSetup.zaBoundT-1, graphSetup.zaBoundW-2, graphSetup.zaBoundH-2);

        GLES20.glUniformMatrix4fv(mapPositionMatrixHandle, 1, false, graphSetup.zScaleMatrix, 0);
        GLES20.glUniform1i(mapLogXYZHandle, graphSetup.logZ ? 0x05 : 0x00);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboZScaleX);
        GLES20.glVertexAttribPointer(mapPositionXHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboZScaleY);
        GLES20.glVertexAttribPointer(mapPositionYHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboZScaleZ);
        GLES20.glVertexAttribPointer(mapPositionZHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glScissor(graphSetup.plotBoundL+1, h-graphSetup.plotBoundH-graphSetup.plotBoundT-1, graphSetup.plotBoundW-2, graphSetup.plotBoundH-2);

        //Clean up

        GLES20.glDisableVertexAttribArray(mapPositionXHandle);
        GLES20.glDisableVertexAttribArray(mapPositionYHandle);
        GLES20.glDisableVertexAttribArray(mapPositionZHandle);
    }

    public void drawFrame() {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);

        GLES20.glScissor(graphSetup.plotBoundL+1, h-graphSetup.plotBoundH-graphSetup.plotBoundT-1, graphSetup.plotBoundW-2, graphSetup.plotBoundH-2);

        graphSetup.updateMatrix(this.w, this.h);

        //Draw graph

        int lastValidX = 0;
        List<ExperimentTimeReferenceSet> lastValidXTimeReference = null;
        for (int i = graphSetup.dataSets.size()-1; i >= 0; i--) {
            if (graphSetup.style.get(i) == GraphView.Style.mapXY) {
                drawMap(i);
            }
        }

        drawTimeRanges();
        drawGrid();

        for (int i = graphSetup.dataSets.size()-1; i >= 0; i--) {
            if (graphSetup.style.get(i) == GraphView.Style.mapZ || graphSetup.style.get(i) == GraphView.Style.mapXY) {
                continue;
            }

            if (graphSetup.dataSets.get(i).vboX != 0) {
                lastValidX = graphSetup.dataSets.get(i).vboX;
                lastValidXTimeReference = graphSetup.dataSets.get(i).timeReferencesX;
            }

            drawCurve(i, lastValidX, lastValidXTimeReference);
        }

        GLES20.glUseProgram(0);

    }

    private void doUpdateBuffers() {
        for (CurveData data : graphSetup.dataSets) {
            if (data.vboY == 0 || (data.vboX == 0 && data.fbX != null)) {
                if (data.fbX != null) {
                    int ref[] = new int[2];
                    GLES20.glGenBuffers(2, ref, 0);
                    data.vboX = ref[0];
                    data.vboY = ref[1];
                } else {
                    int ref[] = new int[1];
                    GLES20.glGenBuffers(1, ref, 0);
                    data.vboX = 0;
                    data.vboY = ref[0];
                }
            }
            if (data.ibo == 0 && data.style == GraphView.Style.mapXY) {
                int ref[] = new int[1];
                GLES20.glGenBuffers(1, ref, 0);
                data.ibo = ref[0];
            }

            FloatBufferRepresentation fbX = data.fbX; //Very important: The data might be updated in a parallel process and each Float Buffer representation comes with its own lock. The locking mechanism only makes sense if we use a local pointer that makes sure that we are using the exact FloatBufferRepresantation that has been locked even if the other thread swaps out the entire structure (for example when clear is called on a buffer)
            FloatBufferRepresentation fbY = data.fbY;
            if (fbY != null) {
                synchronized (fbY.lock) {
                    if (fbX != null) {
                        synchronized (fbX.lock) {
                            data.n = Math.min(fbX.size, fbY.size);
                            if (data.n > 0) {

                                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, data.vboX);
                                fbX.data.position(fbX.offset);
                                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.n * 4, fbX.data, GLES20.GL_DYNAMIC_DRAW);

                                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, data.vboY);
                                fbY.data.position(fbY.offset);
                                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.n * 4, fbY.data, GLES20.GL_DYNAMIC_DRAW);

                                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                            }
                        }
                    } else {
                        data.n = fbY.size;

                        if (data.n > 0) {

                            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, data.vboY);
                            fbY.data.position(fbY.offset);
                            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.n * 4, fbY.data, GLES20.GL_DYNAMIC_DRAW);

                            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                        }
                    }
                }
            } else
                data.n = 0;

            if (data.style == GraphView.Style.mapXY && (data.mapWidth > 0)) {
                data.ibUsedCount = ((data.n / data.mapWidth)-1) * (2* data.mapWidth + 2);
                if (data.ibUsedCount > 4 && (data.ib == null || data.ibUsedCount > data.ibCount)) {
                    IntBuffer newIB = ByteBuffer.allocateDirect(data.ibUsedCount * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
                    if (data.ib != null) {
                        newIB.position(0);
                        data.ib.position(0);
                        newIB.put(data.ib);
                    } else {
                        data.ibCount = 0;
                    }
                    data.ib = newIB;
                    data.ib.position(data.ibCount);
                    for (int i = data.ibCount / 2; i < data.ibUsedCount / 2; i++) {
                        int line = i / (data.mapWidth + 1);
                        int x = i % (data.mapWidth + 1);
                        if (x == data.mapWidth) {
                            data.ib.put((line + 1) * data.mapWidth + (x - 1));
                            data.ib.put((line + 1) * data.mapWidth);
                        } else {
                            data.ib.put(line * data.mapWidth + x);
                            data.ib.put((line + 1) * data.mapWidth + x);
                        }
                    }
                    data.ibCount = data.ibUsedCount;
                }
                if (data.ibUsedCount > 4) {
                    data.ib.position(0);
                    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, data.ibo);
                    GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, data.ibUsedCount * 4, data.ib, GLES20.GL_DYNAMIC_DRAW);
                    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                }
            }
        }
    }

    private void doUpdateGrid() {
        nGridLines = 0;
        if (graphSetup.xTics == null || graphSetup.yTics == null)
            return;

        FloatBuffer gridData = ByteBuffer.allocateDirect((graphSetup.xTics.length + graphSetup.yTics.length) * 2 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (double x : graphSetup.xTics) {
            gridData.put((float)(graphSetup.logX ? Math.log(x) : x));
            gridData.put((float)(graphSetup.logY ? Math.log(graphSetup.minY): graphSetup.minY));
            gridData.put((float)(graphSetup.logX ? Math.log(x) : x));
            gridData.put((float)(graphSetup.logY ? Math.log(graphSetup.maxY): graphSetup.maxY));
            nGridLines++;
        }
        for (double y : graphSetup.yTics) {
            gridData.put((float)(graphSetup.logX ? Math.log(graphSetup.minX): graphSetup.minX));
            gridData.put((float)(graphSetup.logY ? Math.log(y) : y));
            gridData.put((float)(graphSetup.logX ? Math.log(graphSetup.maxX): graphSetup.maxX));
            gridData.put((float)(graphSetup.logY ? Math.log(y) : y));
            nGridLines++;
        }
        gridData.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboGrid);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, nGridLines * 2 * 2 * 4, gridData, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        nZGridLines = 0;
        if (graphSetup.zTics == null)
            return;

        FloatBuffer zGridData = ByteBuffer.allocateDirect((graphSetup.zTics.length) * 2 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (double z : graphSetup.zTics) {
            zGridData.put((float)(graphSetup.logZ ? Math.log(z) : z));
            zGridData.put((float)(0.));
            zGridData.put((float)(graphSetup.logZ ? Math.log(z) : z));
            zGridData.put((float)(1.));
            nZGridLines++;
        }
        zGridData.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboZGrid);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, nZGridLines * 2 * 2 * 4, zGridData, GLES20.GL_DYNAMIC_DRAW);

        FloatBuffer zRange = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        zRange.put(graphSetup.minZ).put(graphSetup.minZ).put(graphSetup.maxZ).put(graphSetup.maxZ);

        FloatBuffer zScaleY = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        zScaleY.put(0).put(1).put(0).put(1);

        zRange.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboZScaleX);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 4 * 4, zRange, GLES20.GL_DYNAMIC_DRAW);

        zScaleY.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboZScaleY);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 4 * 4, zScaleY, GLES20.GL_DYNAMIC_DRAW);

        zRange.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboZScaleZ);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 4 * 4, zRange, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        if (graphSetup.colorScale.size() > 1) {
            int nSteps = graphSetup.colorScale.size();
            ByteBuffer colorScaleTextureData = ByteBuffer.allocateDirect(nSteps * 3).order(ByteOrder.nativeOrder());
            for (int i = 0; i < nSteps; i++) {
                int c = graphSetup.colorScale.get(i);
                colorScaleTextureData.put((byte)(c >> 16)).put((byte)(c >> 8)).put((byte)c);
            }
            colorScaleTextureData.position(0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, colorScaleTexture);
            GLES20.glTexImage2D ( GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, nSteps,1,0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, colorScaleTextureData);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindTexture ( GLES20.GL_TEXTURE_2D, 0 );
        }
    }

    private void doUpdateTimeRanges() {
        nTimeRanges = 0;
        if (graphSetup.trStarts == null || graphSetup.trStops == null || graphSetup.trStarts.size() == 0 || graphSetup.trStops.size() == 0)
            return;
        if (!(graphSetup.timeOnX || graphSetup.timeOnY))
            return;

        FloatBuffer timeRangesData = ByteBuffer.allocateDirect(graphSetup.trStarts.size() * 4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int i = 0; i < graphSetup.trStarts.size() && i < graphSetup.trStops.size(); i++) {
            if (graphSetup.timeOnX) {
                float x1r = (float) (Double.isNaN(graphSetup.trStops.get(i)) ? graphSetup.minX : (graphSetup.trStops.get(i) + (graphSetup.absoluteTime ? graphSetup.systemTimeReferenceGap.get(i-1) : 0.0)));
                float x2r = (float) (Double.isNaN(graphSetup.trStarts.get(i)) ? graphSetup.maxX : (graphSetup.trStarts.get(i) + (graphSetup.absoluteTime ? graphSetup.systemTimeReferenceGap.get(i) : 0.0)));
                float x1 = (float)(graphSetup.logX ? Math.log(x1r) : x1r);
                float x2 = (float)(graphSetup.logX ? Math.log(x2r) : x2r);
                float y1 = (float) (graphSetup.logY ? Math.log(graphSetup.minY) : graphSetup.minY);
                float y2 = (float) (graphSetup.logY ? Math.log(graphSetup.maxY) : graphSetup.maxY);
                timeRangesData.put(x1);
                timeRangesData.put(y1);
                timeRangesData.put(x1);
                timeRangesData.put(y2);
                timeRangesData.put(x2);
                timeRangesData.put(y1);
                timeRangesData.put(x2);
                timeRangesData.put(y2);
                nTimeRanges++;
            }
            if (graphSetup.timeOnY) {
                float x1 = (float) (graphSetup.logX ? Math.log(graphSetup.minX) : graphSetup.minX);
                float x2 = (float) (graphSetup.logX ? Math.log(graphSetup.maxX) : graphSetup.maxX);
                float y1r = (float) (Double.isNaN(graphSetup.trStops.get(i)) ? graphSetup.minY : (graphSetup.trStops.get(i) + (graphSetup.absoluteTime ? graphSetup.systemTimeReferenceGap.get(i-1) : 0.0)));
                float y2r = (float) (Double.isNaN(graphSetup.trStarts.get(i)) ? graphSetup.maxY : (graphSetup.trStarts.get(i) + (graphSetup.absoluteTime ? graphSetup.systemTimeReferenceGap.get(i) : 0.0)));
                float y1 = (float)(graphSetup.logY ? Math.log(y1r) : y1r);
                float y2 = (float)(graphSetup.logY ? Math.log(y2r) : y2r);
                timeRangesData.put(x1);
                timeRangesData.put(y1);
                timeRangesData.put(x2);
                timeRangesData.put(y1);
                timeRangesData.put(x1);
                timeRangesData.put(y2);
                timeRangesData.put(x2);
                timeRangesData.put(y2);
                nTimeRanges++;
            }
        }
        timeRangesData.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboTimeRanges);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, nTimeRanges * 4 * 2 * 4, timeRangesData, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    ////// Careful, all methods below will be called from UI thread /////

    public void setGraphSetup(GraphSetup gs) {
        this.graphSetup = gs;
    }

    public void requestRender() {
        if (!readyToRender)
            return;
        synchronized (lock) {
            renderRequested = true;
            lock.notify();
        }
    }

    public void notifyUpdateBuffers() {
        updateBuffers = true;
    }

    public void notifyUpdateGrid() {
        updateGrid = true;
    }

    public void notifyUpdateTimeRanges() {
        updateTimeRanges = true;
    }

    public void halt() {
        synchronized (lock) {
            done = true;
            lock.notify();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        synchronized (lock) {
            surfaceTexture = st;
            this.w = width;
            this.h = height;
            lock.notify();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        synchronized (lock) {
            newSurface = st;
            this.w = width;
            this.h = height;
            updateBuffers = true;
            updateGrid = true;
            updateTimeRanges = true;
            lock.notify();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        synchronized (lock) {
            surfaceTexture = null;
            lock.notify();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {

    }

}
