package de.rwth_aachen.phyphox.ExperimentView.GraphView;

import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;

import java.io.Serializable;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.ExperimentTimeReferenceSet;
import de.rwth_aachen.phyphox.FloatBufferRepresentation;

public class GraphSetup implements Serializable {
    public boolean incrementalX = false;

    public int plotBoundL, plotBoundT, plotBoundW, plotBoundH;
    int zaBoundL, zaBoundT, zaBoundW, zaBoundH;
    double minX, maxX, minY, maxY, minZ, maxZ;
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
    public boolean interpolateMapColors = true; //If false, color maps show a homogeneously colored cell around each data point instead of interpolating colors between the data points

    public Vector<Integer> colorScale = new Vector<>();

    public GraphView.Tic[] xTics = null;
    public GraphView.Tic[] yTics = null;
    public GraphView.Tic[] zTics = null;

    public List<Double> trStarts = null;
    public List<Double> trStops = null;
    public List<Double> systemTimeReferenceGap = null;
    public boolean timeOnX = false;
    public boolean timeOnY = false;
    public boolean absoluteTime = false;
    public boolean linearTime = false;
    public boolean hideTimeMarkers = false;

    private OnRenderedPlotResizedListener OnRenderedPlotResizedListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // A Callback that execute and update the height of the calibration marker.
    public interface OnRenderedPlotResizedListener {
        void onSurfaceResized(int graphHeight);
    }

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

        // Notify the listener on UI thread so that the listener gets the updated rendered height of the plot.
        if (OnRenderedPlotResizedListener != null) {
            mainHandler.post(() -> OnRenderedPlotResizedListener.onSurfaceResized(plotBoundH));
        }
    }

    public void setOnRenderedPlotResizedListener(OnRenderedPlotResizedListener listener) {
        this.OnRenderedPlotResizedListener = listener;
    }

    public void setZAxisBounds(float l, float t, float w, float h) {
        zaBoundL = Math.round(l);
        zaBoundT = Math.round(t);
        zaBoundW = Math.round(w);
        zaBoundH = Math.round(h);
    }

    public void setTics(GraphView.Tic[] xTics, GraphView.Tic[] yTics, GraphView.Tic[] zTics, PlotRenderer plotRenderer) {
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
            l = (float)minX - plotBoundL / (float) plotBoundW * ((float)maxX - (float)minX);
            r = (float)maxX + (w - plotBoundW - plotBoundL) / (float) plotBoundW * ((float)maxX - (float)minX);
        }
        if (logY) {
            float logMinY = (float)Math.log(minY);
            float logMaxY = (float)Math.log(maxY);
            b = logMinY - (h - plotBoundT - plotBoundH) / (float) plotBoundH * (logMaxY - logMinY);
            t = logMaxY + (plotBoundT) / (float) plotBoundH * (logMaxY - logMinY);
        } else {
            b = (float)minY - (h - plotBoundT - plotBoundH) / (float) plotBoundH * ((float)maxY - (float)minY);
            t = (float)maxY + (plotBoundT) / (float) plotBoundH * ((float)maxY - (float)minY);
        }

        //The double ranges above differ, but the float versions may not; orthoM throws on equal bounds
        if (l == r || b == t)
            return;

        if (style.contains(GraphView.Style.mapXY) && maxZ != minZ) {
            float zminOnX, zmaxOnX, zmin, zmax;
            if (logZ) {
                zmin = (float) Math.log(minZ);
                zmax = (float) Math.log(maxZ);
            } else {
                zmin = (float)minZ;
                zmax = (float)maxZ;
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
                    newData.color[3] = ((color.get(i) >>> 24) & 0xff)/255.f; //the alpha byte of an RRGGBBAA color (file format 1.21); ff for every older color
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
