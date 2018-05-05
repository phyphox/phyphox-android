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
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
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
    int n;
    float color[] = new float[4];
    transient floatBufferRepresentation fbX, fbY;
}

class GraphSetup {
    private final Object lock = new Object();

    int plotBoundL, plotBoundT, plotBoundW, plotBoundH;
    float minX, maxX, minY, maxY;
    public final Vector<CurveData> dataSets = new Vector<>();
    public float[] positionMatrix = new float[16];
    public Vector<Integer> color = new Vector<>();
    public int historyLength = 1;
    public Vector<GraphView.Style> style = new Vector<>();
    public Vector<Float> lineWidth = new Vector<>();
    public boolean logX = false;
    public boolean logY = false;

    public double xTics[] = null;
    public double yTics[] = null;

    GraphSetup() {
        plotBoundL = 0;
        plotBoundT = 0;
        plotBoundW = 0;
        plotBoundH = 0;
        Matrix.setIdentityM(positionMatrix, 0);
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

    public void setTics(double xTics[], double yTics[], PlotRenderer plotRenderer) {
        this.xTics = xTics;
        this.yTics = yTics;
        plotRenderer.notifyUpdateGrid();
    }

    public void setDataBounds(float minX, float maxX, float minY, float maxY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
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
            t = logMaxY + (plotBoundT) / (float) plotBoundH;
        } else {
            b = minY - (h - plotBoundT - plotBoundH) / (float) plotBoundH * (maxY - minY);
            t = maxY + (plotBoundT) / (float) plotBoundH;
        }

        Matrix.orthoM(positionMatrix, 0, l, r, b, t, -1, 1);
    }

    public void setData(floatBufferRepresentation x[], floatBufferRepresentation y[], int n, PlotRenderer plotRenderer) {
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
                dataSets.add(newData);
            }
            final CurveData data = dataSets.get(i);

            data.fbX = x[i];
            data.fbY = y[i];
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
    private SurfaceTexture newSurface = null;

    private GraphSetup graphSetup = null;

    int h, w;
    int bgColor;

    private int glProgram, gridProgram;
    private int positionXHandle, positionYHandle;
    private int colorHandle;
    private int positionMatrixHandle;
    private int logXYHandle;
    private int sizeHandle;

    private int gridPositionHandle;
    private int gridMatrixHandle;
    private int nGridLines = 0;
    int vboGrid = 0;

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

            "float posX, posY;" +

            "void main () {" +
            "   v_color = in_color;" +
            "   if (logXY == 1 || logXY == 3)" +
            "       posX = log(positionX);" +
            "   else" +
            "       posX = positionX;" +
            "   if (logXY >= 2)" +
            "       posY = log(positionY);" +
            "   else" +
            "       posY = positionY;" +
            "   gl_Position = positionMatrix * vec4(posX, posY, 0., 1.);" +
            "   gl_PointSize = size;" +
            "}";

    final String fragmentShader =
            "precision mediump float;" +

            "varying vec4 v_color;" +

            "void main () {" +
            "   gl_FragColor = v_color;" +
            "}";

    final String gridShader =
            "attribute vec2 position;" +
            "uniform mat4 positionMatrix;" +

            "void main () {" +
            "   gl_Position = positionMatrix * vec4(position, 0., 1.);" +
            "}";

    final String gridFragmentShader =
            "void main () {" +
            "   gl_FragColor = vec4(1.0, 1.0, 1.0, 0.4);" +
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

        int ref[] = new int[1];
        GLES20.glGenBuffers(1, ref, 0);
        vboGrid = ref[0];

        GLES20.glUseProgram(0);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    public void deinitScene() {

    }

    public void drawFrame() {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);

        GLES20.glScissor(graphSetup.plotBoundL+1, h-graphSetup.plotBoundH-graphSetup.plotBoundT-1, graphSetup.plotBoundW-2, graphSetup.plotBoundH-2);

        graphSetup.updateMatrix(this.w, this.h);

        //Draw grid

        GLES20.glUseProgram(gridProgram);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboGrid);
        GLES20.glEnableVertexAttribArray(gridPositionHandle);
        GLES20.glVertexAttribPointer(gridPositionHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glUniformMatrix4fv(gridMatrixHandle, 1, false, graphSetup.positionMatrix, 0);

        GLES20.glLineWidth(1.f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2*nGridLines);

        GLES20.glDisableVertexAttribArray(gridPositionHandle);

        //Draw graph

        GLES20.glUseProgram(glProgram);

        GLES20.glUniformMatrix4fv(positionMatrixHandle, 1, false, graphSetup.positionMatrix, 0);
        GLES20.glUniform1i(logXYHandle, (graphSetup.logX ? 0x01 : 0x00) | (graphSetup.logY ? 0x02 : 0x00));

        int lastValidX = 0;
        for (int i = graphSetup.dataSets.size()-1; i >= 0; i--) {
            GLES20.glLineWidth(2.f*graphSetup.lineWidth.get(i));

            CurveData dataSet = graphSetup.dataSets.get(i);
            if (dataSet.n == 0 || (dataSet.n < 2 && !(graphSetup.style.get(i) == GraphView.Style.dots)))
                continue;

            if (dataSet.vboX != 0)
                lastValidX = dataSet.vboX;
            if (lastValidX == 0)
                continue;
            GLES20.glEnableVertexAttribArray(positionXHandle);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, lastValidX);
            GLES20.glVertexAttribPointer(positionXHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dataSet.vboY);
            GLES20.glEnableVertexAttribArray(positionYHandle);
            GLES20.glVertexAttribPointer(positionYHandle, 1, GLES20.GL_FLOAT, false, 0, 0);

            GLES20.glUniform4fv(colorHandle, 1, dataSet.color, 0);

            if (graphSetup.style.get(i) == GraphView.Style.dots) {
                GLES20.glUniform1f(sizeHandle, graphSetup.lineWidth.get(i) * 4.f);
                GLES20.glDrawArrays(GLES20.GL_POINTS, 0, dataSet.n);
            } else if (graphSetup.style.get(i) == GraphView.Style.hbars || graphSetup.style.get(i) == GraphView.Style.vbars) {
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, dataSet.n);
            } else {
                GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, dataSet.n);
            }
        }

        GLES20.glDisableVertexAttribArray(positionXHandle);
        GLES20.glDisableVertexAttribArray(positionYHandle);
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

            if (data.fbY != null) {
                synchronized (data.fbY.lock) {
                    if (data.fbX != null) {
                        synchronized (data.fbX.lock) {
                            data.n = Math.min(data.fbX.size, data.fbY.size);
                            if (data.n > 0) {

                                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, data.vboX);
                                data.fbX.data.position(data.fbX.offset);
                                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.n * 4, data.fbX.data, GLES20.GL_DYNAMIC_DRAW);

                                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, data.vboY);
                                data.fbY.data.position(data.fbY.offset);
                                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.n * 4, data.fbY.data, GLES20.GL_DYNAMIC_DRAW);

                                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                            }
                        }
                    } else {
                        data.n = data.fbY.size;

                        if (data.n > 0) {

                            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, data.vboY);
                            data.fbY.data.position(data.fbY.offset);
                            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.n * 4, data.fbY.data, GLES20.GL_DYNAMIC_DRAW);

                            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                        }
                    }
                }
            } else
                data.n = 0;
        }
    }

    private void doUpdateGrid() {
        nGridLines = 0;
        if (graphSetup.xTics == null || graphSetup.yTics == null)
            return;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboGrid);
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
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, nGridLines * 2 * 2 * 4, gridData, GLES20.GL_DYNAMIC_DRAW);
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