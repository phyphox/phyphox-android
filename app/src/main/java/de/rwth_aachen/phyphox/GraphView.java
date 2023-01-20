package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.preference.PreferenceManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import de.rwth_aachen.phyphox.Helper.Helper;

//The graphView class implements an Android view which displays a data graph

public class GraphView extends View {
    public enum Style {
        lines, dots, hbars, vbars, mapXY, mapZ, unknown;
    }

    public static Style styleFromStr(String str) {
        switch (str) {
            case "lines": return Style.lines;
            case "dots": return Style.dots;
            case "hbars": return Style.hbars;
            case "vbars": return Style.vbars;
            case "map": return Style.mapXY;
            case "mapZ": return Style.mapZ;
        }
        return Style.unknown;
    }

    public enum TouchMode {
        off, zoom, pick;
    }

    interface PointInfo {
        void showPointInfo(float viewX, float viewY, float pointX, float pointY, float pointZ, int index);
        void hidePointInfo(int index);
    }

    private PointInfo pointInfoListener= null;
    public void setPointInfoListener (PointInfo pointInfoListener){
        this.pointInfoListener = pointInfoListener;
    }
    final int maxPicked = 2;
    private int pickedPointIndex[] = new int[maxPicked];
    private int pickedPointGraphIndex[] = new int[maxPicked];

    private FloatBufferRepresentation[] graphX; //The x data to be displayed
    private double[] histMinX, histMaxX;
    private FloatBufferRepresentation[] graphY; //The y data to be displayed
    private double[] histMinY, histMaxY;
    private double histMinZ, histMaxZ;

    private List<ExperimentTimeReferenceSet>[] timeReferencesX;
    private List<ExperimentTimeReferenceSet>[] timeReferencesY;

    private double aspectRatio = 3.;

    private int historyLength; //If set to n > 1 the graph will also show the last n sets in a different color
    private int nCurves; //Tracks the number of entries in the history

    Style[] style; //Styles for each graph
    int[] mapWidth; //MapWidth for each graph (if applicable)
    Vector<Integer> colorScale = new Vector<>();

    private final static int maxXRegularTics = 5; //Constant to set a target number of tics on the x axis
    private final static int maxYRegularTics = 5; //Constant to set a target number of tics on the y axis
    private final static int maxZRegularTics = 5; //Constant to set a target number of tics on the y axis
    private final static int maxXTimeTics = 4; //Constant to set a target number of tics on the x axis if time is plotted
    private final static int maxYTimeTics = 5; //Constant to set a target number of tics on the y axis if time is plotted
    private String labelX = null; //Label for the x-axis
    private String labelY = null; //Label for the y-axis
    private String labelZ = null; //Label for the y-axis
    private String unitX = null; //Label for the x-axis
    private String unitY = null; //Label for the y-axis
    private String unitZ = null; //Label for the y-axis
    private String unitYX = null; //Unit for the slope, i.e. y/x
    public boolean timeOnX = false; //x-axis is time axis?
    public boolean timeOnY = false; //y-axis is time axis?
    public boolean absoluteTime = false; //Use system time on time axis instead of experiment time
    public boolean linearTime = false; //Time data is given in seconds since 1970, ignoring pauses (in constrast to experiment time)
    public boolean hideTimeMarkers = false; //Do not show the red markers that indicate times while the phyphox experiment was not running.
    public boolean logX = false; //logarithmic scale for the x-axis?
    public boolean logY = false; //logarithmic scale for the y-axis?
    public boolean logZ = false; //logarithmic scale for the y-axis?
    private int xPrecision = -1;
    private int yPrecision = -1;
    private int zPrecision = -1;

    public class Tic {
        double value;
        int precision;
        Tic(double value, int precision) {
            this.value = value;
            this.precision = precision;
        }
    }

    private double[] lineWidth;
    private int[] color;

    public boolean previouslyKept = false; //Keeps track if the user has kept his zoom when he left the interactive mode the last time

    public enum scaleMode {
        auto, extend, fixed
    }

    scaleMode scaleMinX = scaleMode.auto;
    scaleMode scaleMaxX = scaleMode.auto;
    scaleMode scaleMinY = scaleMode.auto;
    scaleMode scaleMaxY = scaleMode.auto;
    scaleMode scaleMinZ = scaleMode.auto;
    scaleMode scaleMaxZ = scaleMode.auto;

    double minX = 0.;
    double maxX = 0.;
    double minY = 0.;
    double maxY = 0.;
    double minZ = 0.;
    double maxZ = 0.;

    boolean followX = false;

    private TouchMode touchMode = TouchMode.off;
    public class ZoomState {
        double minX = Double.NaN;
        double maxX = Double.NaN;
        double minY = Double.NaN;
        double maxY = Double.NaN;
        double minZ = Double.NaN;
        double maxZ = Double.NaN;
        boolean follows = false;
    }

    public ZoomState zoomState = new ZoomState();

    Paint paint; //Anti-Aliased paint used all over this class

    PlotAreaView plotAreaView;
    PlotRenderer plotRenderer;
    GraphSetup graphSetup;

    private boolean processingGesture = false;
    private boolean gestureOnZScale = false;
    private float panXStart = Float.NaN;
    private float panYStart = Float.NaN;
    private double panXOrigin = Double.NaN;
    private double panYOrigin = Double.NaN;
    private double panWOrigin = Double.NaN;
    private double panHOrigin = Double.NaN;

    private float scaleXStart = Float.NaN;
    private float scaleYStart = Float.NaN;
    private float scaleXStartSpan = Float.NaN;
    private float scaleYStartSpan = Float.NaN;
    private double scaleXOrigin = Double.NaN;
    private double scaleYOrigin = Double.NaN;
    private double scaleXStartMin = Double.NaN;
    private double scaleYStartMin = Double.NaN;
    private double scaleXStartMax = Double.NaN;
    private double scaleYStartMax = Double.NaN;

    private float pickXStart = Float.NaN;
    private float pickYStart = Float.NaN;

    private ScaleGestureDetector.OnScaleGestureListener scaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            scaleXStart = scaleGestureDetector.getFocusX();
            scaleYStart = scaleGestureDetector.getFocusY();

            if (scaleXStart > graphSetup.plotBoundL && scaleXStart < graphSetup.plotBoundL + graphSetup.plotBoundW && scaleYStart > graphSetup.plotBoundT && scaleYStart < graphSetup.plotBoundT + graphSetup.plotBoundH) {
                gestureOnZScale = false;
                zoomState.follows = false;
            } else if (scaleXStart > graphSetup.zaBoundL && scaleXStart < graphSetup.zaBoundL + graphSetup.zaBoundW && scaleYStart > graphSetup.zaBoundT && scaleYStart < graphSetup.zaBoundT + 2*graphSetup.zaBoundH)
                gestureOnZScale = true;
            else
                return false;

            scaleXStartSpan = Math.abs(scaleGestureDetector.getCurrentSpanX());
            scaleYStartSpan = Math.abs(scaleGestureDetector.getCurrentSpanY());


            final double cminY = Double.isNaN(zoomState.minY) ? minY : zoomState.minY;
            final double cmaxY = Double.isNaN(zoomState.maxY) ? maxY : zoomState.maxY;
            final double cminX;
            final double cmaxX;
            if (gestureOnZScale) {
                cminX = Double.isNaN(zoomState.minZ) ? minZ : zoomState.minZ;
                cmaxX = Double.isNaN(zoomState.maxZ) ? maxZ : zoomState.maxZ;
            } else {
                cminX = Double.isNaN(zoomState.minX) ? minX : zoomState.minX;
                cmaxX = Double.isNaN(zoomState.maxX) ? maxX : zoomState.maxX;
            }


            scaleXStartMin = cminX;
            scaleYStartMin = cminY;
            scaleXStartMax = cmaxX;
            scaleYStartMax = cmaxY;
            if (gestureOnZScale)
                scaleXOrigin = viewXToDataZ(scaleXStart);
            else
                scaleXOrigin = viewXToDataX(scaleXStart);
            scaleYOrigin = viewYToDataY(scaleYStart);

            processingGesture = false;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            final float spanX = Math.abs(scaleGestureDetector.getCurrentSpanX());
            final float spanY = Math.abs(scaleGestureDetector.getCurrentSpanY());
            final float focusX = scaleGestureDetector.getFocusX();
            final float focusY = scaleGestureDetector.getFocusY();

            double scaleX, scaleY;

            if (scaleXStartSpan/scaleYStartSpan > 0.5)
                scaleX = spanX / scaleXStartSpan;
            else
                scaleX = 1.;

            if (scaleYStartSpan/scaleXStartSpan > 0.5)
                scaleY = spanY / scaleYStartSpan;
            else
                scaleY = 1.;

            if (scaleX > 20)
                scaleX = 20;

            if (scaleY > 20)
                scaleY = 20;

            if (gestureOnZScale) {
                zoomState.minZ = Math.max(Math.min(viewXToDataZ(dataZToViewX(scaleXOrigin) - (dataZToViewX(scaleXOrigin) - dataZToViewX(scaleXStartMin)) / scaleX + scaleXStart - focusX), 1e38), -1e38);
                zoomState.maxZ = Math.max(Math.min(viewXToDataZ(dataZToViewX(scaleXOrigin) - (dataZToViewX(scaleXOrigin) - dataZToViewX(scaleXStartMax)) / scaleX + scaleXStart - focusX), 1e38), -1e38);
            } else {
                zoomState.minX = Math.max(Math.min(viewXToDataX(dataXToViewX(scaleXOrigin) - (dataXToViewX(scaleXOrigin) - dataXToViewX(scaleXStartMin)) / scaleX + scaleXStart - focusX), 1e38), -1e38);
                zoomState.minY = Math.max(Math.min(viewYToDataY(dataYToViewY(scaleYOrigin) - (dataYToViewY(scaleYOrigin) - dataYToViewY(scaleYStartMin)) / scaleY + scaleYStart - focusY), 1e38), -1e38);
                zoomState.maxX = Math.max(Math.min(viewXToDataX(dataXToViewX(scaleXOrigin) - (dataXToViewX(scaleXOrigin) - dataXToViewX(scaleXStartMax)) / scaleX + scaleXStart - focusX), 1e38), -1e38);
                zoomState.maxY = Math.max(Math.min(viewYToDataY(dataYToViewY(scaleYOrigin) - (dataYToViewY(scaleYOrigin) - dataYToViewY(scaleYStartMax)) / scaleY + scaleYStart - focusY), 1e38), -1e38);
            }

            invalidate();

            processingGesture = false;

            return true;
        }

    };
    private ScaleGestureDetector scaleGestureDetector;

    //Simple constructor just needs a context to call the View constructor
    //Initialize some stuff...
    public GraphView(Context context, PlotAreaView plotAreaView, PlotRenderer plotRenderer) {
        super(context);
        for (int i = 0; i < maxPicked; i++)
            pickedPointIndex[i] = -1;
        this.plotAreaView = plotAreaView;
        this.plotRenderer = plotRenderer;
        this.graphSetup = new GraphSetup();
        this.plotRenderer.setGraphSetup(this.graphSetup);
        setHistoryLength(1);
        rescale(); //Calculate initial scale. At this point it will just set all min and max to +inf and -inf
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scaleGestureDetector = new ScaleGestureDetector(context, scaleListener);
    }

    public void resetPicks() {
        for (int i = 0; i < maxPicked; i++) {
            pickedPointIndex[i] = -1;
            pointInfoListener.hidePointInfo(i);
        }
    }

    public void setTouchMode(TouchMode touchMode) {
        this.touchMode = touchMode;
        if (touchMode == TouchMode.off) {
            resetPicks();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (touchMode) {
            case zoom:
                return onTouchEventZoom(event);
            case pick:
                return onTouchEventPick(event);
        }
        return super.onTouchEvent(event);
    }

    private void highlightNearestPoint(float x, float y, int index) {

        double minDist = Double.POSITIVE_INFINITY;
        int minIndex = -1;
        int minGraphIndex = -1;
        double minX = Double.NaN;
        double minY = Double.NaN;
        double minZ = Double.NaN;
        double minVX = Double.NaN;
        double minVY = Double.NaN;

        final int range = 100;

        double searchRangeMaxX = Math.max(viewXToDataX(x+range), viewXToDataX(x-range));
        double searchRangeMinX = Math.min(viewXToDataX(x+range), viewXToDataX(x-range));
        double searchRangeMaxY = Math.max(viewYToDataY(y+range), viewYToDataY(y-range));
        double searchRangeMinY = Math.min(viewYToDataY(y+range), viewYToDataY(y-range));

        if (timeOnX) {
            double offset = offsetFromSystemTime(viewXToDataX(x));
            searchRangeMaxX -= offset;
            searchRangeMinX -= offset;
        }
        if (timeOnY) {
            double offset = offsetFromSystemTime(viewYToDataY(y));
            searchRangeMaxY -= offset;
            searchRangeMinY -= offset;
        }

        for (int i = 0; i < graphSetup.dataSets.size(); i++) {
            CurveData cd = graphSetup.dataSets.get(i);
            if (cd.style == Style.mapZ || cd.fbX == null || cd.fbY == null)
                continue;
            double vxi, vyi, dx, dy, d;
            int n = cd.n;
            int offX = cd.fbX.offset;
            int offY = cd.fbY.offset;
            int offZ = 0;
            float[] xi = new float[cd.fbX.offset + n];
            float[] yi = new float[cd.fbY.offset + n];
            float[] zi = null;
            if (i+1 < graphSetup.dataSets.size() && graphSetup.dataSets.get(i+1).style == Style.mapZ) {
                offZ = graphSetup.dataSets.get(i + 1).fbY.offset;
                zi = new float[offZ + n];
            }
            try {
                cd.fbX.data.position(0);
                cd.fbY.data.position(0);
                cd.fbX.data.get(xi, 0, offX + n);
                cd.fbY.data.get(yi, 0, offY + n);
                if (i+1 < graphSetup.dataSets.size() && graphSetup.dataSets.get(i+1).style == Style.mapZ) {
                    graphSetup.dataSets.get(i+1).fbY.data.position(0);
                    graphSetup.dataSets.get(i+1).fbY.data.get(zi, 0, offZ + n);
                }
            } catch (Exception e) {
                break;
            }
            for (int j = 0; j < cd.n; j++) {

                if (cd.style == Style.hbars || cd.style == Style.vbars) {
                    if (j % 6 != 2 && j % 6 != 3)
                        continue;
                }

                if (xi[offX + j] < searchRangeMinX || xi[offX + j] > searchRangeMaxX || yi[offY + j] < searchRangeMinY || yi[offY + j] > searchRangeMaxY)
                    continue;
                vxi = dataXToViewX(xi[offX + j] + (timeOnX ? offsetFromExperimentTime(xi[offX + j]) : 0.0));
                vyi = dataYToViewY(yi[offY + j] + (timeOnY ? offsetFromExperimentTime(xi[offX + j]) : 0.0));

                dx = vxi - x;
                dy = vyi - y;
                d = dx*dx+dy*dy;
                if (d < range*range && d < minDist) {
                    minDist = d;
                    minIndex = j;
                    minGraphIndex = i;
                    minX = xi[offX + j];
                    minY = yi[offY + j];
                    if (zi != null)
                        minZ = zi[offZ + j];
                    else
                        minZ = Double.NaN;
                    minVX = vxi;
                    minVY = vyi;
                }
            }
        }

        pointInfoListener.showPointInfo((float)minVX, (float)minVY, (float)minX, (float)minY, (float)minZ, index);
        pickedPointIndex[index] = minIndex;
        pickedPointGraphIndex[index] = minGraphIndex;
    }

    private boolean onTouchEventPick(MotionEvent event) {

        if (pointInfoListener == null)
            return true;

        final float x = event.getX();
        final float y = event.getY();

        if (!processingGesture
                && (!(x > graphSetup.plotBoundL && x < graphSetup.plotBoundL + graphSetup.plotBoundW && y > graphSetup.plotBoundT && y < graphSetup.plotBoundT + graphSetup.plotBoundH)))
            return super.onTouchEvent(event);

        final int action = event.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                processingGesture = true;

                pickXStart = x;
                pickYStart = y;

                pickedPointIndex[1] = -1;
                pointInfoListener.hidePointInfo(1);
                highlightNearestPoint(x, y, 0);

                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_MOVE: {

                final float dx = x - pickXStart;
                final float dy = y - pickYStart;

                if (!processingGesture || (dx*dx+dy*dy < 1000))
                    break;

                highlightNearestPoint(x, y, 1);

                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_UP: {

                processingGesture = false;

                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {

                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            }
        }

        return true;
    }

    private boolean onTouchEventZoom(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);

        if (event.getPointerCount() > 1)
            return true;
        final float x = event.getX();
        final float y = event.getY();

        if (!processingGesture) {
            if (x > graphSetup.plotBoundL && x < graphSetup.plotBoundL + graphSetup.plotBoundW && y > graphSetup.plotBoundT && y < graphSetup.plotBoundT + graphSetup.plotBoundH) {
                gestureOnZScale = false;
                zoomState.follows = false;
            } else if (x > graphSetup.zaBoundL && x < graphSetup.zaBoundL + graphSetup.zaBoundW && y > graphSetup.zaBoundT && y < graphSetup.zaBoundT + 2 * graphSetup.zaBoundH)
                gestureOnZScale = true;
            else
                return super.onTouchEvent(event);
        }

        final int action = event.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                panXStart = x;
                panYStart = y;
                panXOrigin = Double.NaN;
                panYOrigin = Double.NaN;
                panWOrigin = Double.NaN;
                panHOrigin = Double.NaN;
                processingGesture = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final float dx = x - panXStart;
                final float dy = y - panYStart;

                if (!processingGesture || (dx*dx+dy*dy < 30 && Double.isNaN(panXOrigin)))
                    break;

                if (Double.isNaN(panXOrigin)) {
                    if (gestureOnZScale) {
                        panXOrigin = Double.isNaN(zoomState.minZ) ? minZ : zoomState.minZ;
                        panWOrigin = (Double.isNaN(zoomState.maxZ) ? maxZ : zoomState.maxZ) - (Double.isNaN(zoomState.minZ) ? minZ : zoomState.minZ);
                    } else {
                        panXOrigin = Double.isNaN(zoomState.minX) ? minX : zoomState.minX;
                        panYOrigin = Double.isNaN(zoomState.minY) ? minY : zoomState.minY;
                        panWOrigin = (Double.isNaN(zoomState.maxX) ? maxX : zoomState.maxX) - (Double.isNaN(zoomState.minX) ? minX : zoomState.minX);
                        panHOrigin = (Double.isNaN(zoomState.maxY) ? maxY : zoomState.maxY) - (Double.isNaN(zoomState.minY) ? minY : zoomState.minY);
                    }
                }

                if (gestureOnZScale) {
                    zoomState.minZ = Math.max(Math.min(viewXToDataZ(dataZToViewX(panXOrigin) - dx), 1e38), -1e38);
                    zoomState.maxZ = Math.max(Math.min(viewXToDataZ(dataZToViewX(panXOrigin + panWOrigin) - dx), 1e38), -1e38);
                } else {
                    zoomState.minX = Math.max(Math.min(viewXToDataX(dataXToViewX(panXOrigin) - dx), 1e38), -1e38);
                    zoomState.minY = Math.max(Math.min(viewYToDataY(dataYToViewY(panYOrigin) - dy), 1e38), -1e38);
                    zoomState.maxX = Math.max(Math.min(viewXToDataX(dataXToViewX(panXOrigin + panWOrigin) - dx), 1e38), -1e38);
                    zoomState.maxY = Math.max(Math.min(viewYToDataY(dataYToViewY(panYOrigin + panHOrigin) - dy), 1e38), -1e38);
                }

                invalidate();

                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (processingGesture && Double.isNaN(panXOrigin))
                    performClick();

                panXOrigin = Double.NaN;
                panYOrigin = Double.NaN;

                processingGesture = false;

                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            }
        }

        return true;

    }

    public void setColor(int color, int i) {
        this.color[i] = color;
        graphSetup.color.set(i, color);
    }

    public void setLineWidth(double lineWidth, int i) {
        this.lineWidth[i] = lineWidth;
        graphSetup.lineWidth.set(i, (float)lineWidth);
    }

    public void setColorScale(Vector<Integer> scale) {
        if (scale != null && scale.size() > 1) {
            graphSetup.colorScale = scale;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int height, width;

        //Measure Width
        if (widthMode == MeasureSpec.UNSPECIFIED && heightMode == MeasureSpec.UNSPECIFIED) {
            width = 600;
            height = (int)Math.round(600/aspectRatio);
        } else if (widthMode == MeasureSpec.UNSPECIFIED) {
            height = heightSize;
            width = (int)Math.round(height * aspectRatio);
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            width = widthSize;
            height = (int)Math.round(width / aspectRatio);
        } else {
            width = widthSize;
            height = heightSize;
        }
        setMeasuredDimension(width, height);
    }

    //Interface to switch between lines and points
    public void setStyle(Style style, int i) {
        this.style[i] = style;
        graphSetup.style.set(i, style);
    }

    public void setMapWidth(int width, int i) {
        this.mapWidth[i] = width;
    }

    //Interface to set the history length
    public void setHistoryLength(int length) {
        setCurves(length);
        this.historyLength = length;
        this.nCurves = 0;
        histMinX = new double[historyLength];
        histMaxX = new double[historyLength];
        histMinY = new double[historyLength];
        histMaxY = new double[historyLength];
        graphSetup.historyLength = length;
    }

    public void setCurves(int n) {
        nCurves = n;
        graphX = new FloatBufferRepresentation[n];
        graphY = new FloatBufferRepresentation[n];
        timeReferencesX = new ArrayList[n];
        timeReferencesY = new ArrayList[n];
        histMinX = new double[n];
        histMaxX = new double[n];
        histMinY = new double[n];
        histMaxY = new double[n];
        color = new int[n];
        style = new Style[n];
        mapWidth = new int[n];
        for (int i = 0; i < n; i++)
            mapWidth[i] = 0;
        lineWidth = new double[n];
        graphSetup.initSize(nCurves);
    }

    public void setAspectRatio(double aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public void setScaleModeX(scaleMode minMode, double minV, scaleMode maxMode, double maxV) {
        scaleMinX = minMode;
        scaleMaxX = maxMode;
        minX = minV;
        maxX = maxV;
        if (minMode == scaleMode.fixed)
            minX = minV;
        else
            minX = Double.POSITIVE_INFINITY;
        if (maxMode == scaleMode.fixed)
            maxX = maxV;
        else
            maxX = Double.NEGATIVE_INFINITY;
    }

    public void setScaleModeY(scaleMode minMode, double minV, scaleMode maxMode, double maxV) {
        scaleMinY = minMode;
        scaleMaxY = maxMode;
        if (minMode == scaleMode.fixed)
            minY = minV;
        else
            minY = Double.POSITIVE_INFINITY;
        if (maxMode == scaleMode.fixed)
            maxY = maxV;
        else
            maxY = Double.NEGATIVE_INFINITY;
    }

    public void setScaleModeZ(scaleMode minMode, double minV, scaleMode maxMode, double maxV) {
        scaleMinZ = minMode;
        scaleMaxZ = maxMode;
        if (minMode == scaleMode.fixed)
            minZ = minV;
        else
            minZ = Double.POSITIVE_INFINITY;
        if (maxMode == scaleMode.fixed)
            maxZ = maxV;
        else
            maxZ = Double.NEGATIVE_INFINITY;
    }

    public void setFollowX(boolean followX) {
        this.followX = followX;
        zoomState.follows = followX;
        if (followX) {
            this.scaleMinX = scaleMode.fixed;
            this.scaleMaxX = scaleMode.fixed;
            zoomState.minX = minX;
            zoomState.maxX = maxX;
        }
    }

    public void setTimeRanges(List<Double> trStarts, List<Double> trStops, List<Double> systemTimeReferenceGap) {
        graphSetup.setTimeRanges(trStarts, trStops, systemTimeReferenceGap, plotRenderer);
    }

    //Rescale any non-fixed ranges
    public void rescale() {
        double dataMinX = Double.POSITIVE_INFINITY;
        double dataMaxX = Double.NEGATIVE_INFINITY;
        double dataMinY = Double.POSITIVE_INFINITY;
        double dataMaxY = Double.NEGATIVE_INFINITY;
        double dataMinZ = Double.POSITIVE_INFINITY;
        double dataMaxZ = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < nCurves && i < historyLength; i++) {
            if (!Double.isInfinite(histMinX[i]) && histMinX[i] < dataMinX)
                dataMinX = histMinX[i];
            if (!Double.isInfinite(histMaxX[i]) && histMaxX[i] > dataMaxX)
                dataMaxX = histMaxX[i];
            if (!Double.isInfinite(histMinY[i]) && histMinY[i] < dataMinY)
                dataMinY = histMinY[i];
            if (!Double.isInfinite(histMaxY[i]) && histMaxY[i] > dataMaxY)
                dataMaxY = histMaxY[i];
        }
        if (!Double.isInfinite(histMinZ))
            dataMinZ = histMinZ;
        if (!Double.isInfinite(histMaxZ))
            dataMaxZ = histMaxZ;

        if (timeOnX && absoluteTime && !linearTime && graphSetup.systemTimeReferenceGap.size() > 0)
            dataMaxX += graphSetup.systemTimeReferenceGap.get(graphSetup.systemTimeReferenceGap.size()-1);
        if (timeOnY && absoluteTime && !linearTime && graphSetup.systemTimeReferenceGap.size() > 0)
            dataMaxY += graphSetup.systemTimeReferenceGap.get(graphSetup.systemTimeReferenceGap.size()-1);

        if (timeOnX && !absoluteTime && linearTime && graphSetup.systemTimeReferenceGap.size() > 0)
            dataMaxX -= graphSetup.systemTimeReferenceGap.get(graphSetup.systemTimeReferenceGap.size()-1);
        if (timeOnY && !absoluteTime && linearTime && graphSetup.systemTimeReferenceGap.size() > 0)
            dataMaxY -= graphSetup.systemTimeReferenceGap.get(graphSetup.systemTimeReferenceGap.size()-1);

        if (scaleMinX == scaleMode.auto || (scaleMinX == scaleMode.extend && minX > dataMinX))
            minX = dataMinX;
        if (scaleMaxX == scaleMode.auto || (scaleMaxX == scaleMode.extend && maxX < dataMaxX))
            maxX = dataMaxX;
        if (scaleMinY == scaleMode.auto || (scaleMinY == scaleMode.extend && minY > dataMinY))
            minY = dataMinY;
        if (scaleMaxY == scaleMode.auto || (scaleMaxY == scaleMode.extend && maxY < dataMaxY))
            maxY = dataMaxY;
        if (scaleMinZ == scaleMode.auto || (scaleMinZ == scaleMode.extend && minZ > dataMinZ))
            minZ = dataMinZ;
        if (scaleMaxZ == scaleMode.auto || (scaleMaxZ == scaleMode.extend && maxZ < dataMaxZ))
            maxZ = dataMaxZ;

        if (!Double.isNaN(zoomState.minX) && !Double.isNaN(zoomState.maxX)) {
            if (zoomState.follows && Double.isFinite(dataMaxX) && Double.isFinite(dataMinX)) {
                double w = zoomState.maxX - zoomState.minX;
                zoomState.minX = dataMaxX - w;
                zoomState.maxX = dataMaxX;
            }
        }
    }

    //Add a new graph and (if enabled) push the old graphs back into history
    public void addGraphData(FloatBufferRepresentation[] graphY, double minY, double maxY, FloatBufferRepresentation[] graphX, double minX, double maxX, double minZ, double maxZ, List<ExperimentTimeReferenceSet>[] timeReferencesX, List<ExperimentTimeReferenceSet>[] timeReferencesY) {
        if (graphY == null || graphX == null || graphX[0] == null || graphY[0] == null)
            return;

        if (historyLength > 1) {
            //Push back data in the history
            for (int i = nCurves - 1; i > 0; i--) {
                this.graphY[i] = this.graphY[i - 1];
                this.graphX[i] = this.graphX[i - 1];
                this.timeReferencesY[i] = this.timeReferencesY[i - 1];
                this.timeReferencesX[i] = this.timeReferencesX[i - 1];
                this.histMinX[i] = this.histMinX[i - 1];
                this.histMaxX[i] = this.histMaxX[i - 1];
                this.histMinY[i] = this.histMinY[i - 1];
                this.histMaxY[i] = this.histMaxY[i - 1];
            }

            //History increases
            nCurves++;
            if (nCurves > historyLength) //History full? Limit it.
                nCurves = historyLength;
        }

        for (int i = 0; i < graphY.length; i++) {
            this.graphY[i] = graphY[i];
            this.timeReferencesY[i] = timeReferencesY[i];
            if (i < graphX.length) {
                this.graphX[i] = graphX[i];
                this.timeReferencesX[i] = timeReferencesX[i];
            } else {
                this.graphX[i] = null;
                this.timeReferencesX[i] = null;
            }
        }
        this.histMinX[0] = minX;
        this.histMaxX[0] = maxX;
        this.histMinY[0] = minY;
        this.histMaxY[0] = maxY;
        this.histMinZ = minZ;
        this.histMaxZ = maxZ;

        graphSetup.setData(this.graphX, this.graphY, this.timeReferencesX, this.timeReferencesY, nCurves, style, mapWidth, plotRenderer);

        //Rescale and invalidate to update everything
        this.rescale();
        this.invalidate();
    }

    //This overloads addGraphData to take pure y-data without x data
    public void addGraphData(FloatBufferRepresentation[] graphY, double min, double max) {
        if (graphY == null || graphY[0] == null)
            return;
        //Create standard x data with indices
        if (graphY[0].size == 0) {
            addGraphData(graphY, min, max, graphY, min, max, min, max, new ArrayList[graphY.length], new ArrayList[graphY.length]);
            return;
        }
        FloatBuffer data = ByteBuffer.allocateDirect(graphY[0].size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        graphY[0].data.position(0);
        for (int i = 0; i < graphY[0].size; i++)
            data.put((float)i);

        FloatBufferRepresentation[] graphX = new FloatBufferRepresentation[graphY.length];
        for (int i = 0; i < graphY.length; i++)
            graphX[i] = null;
        graphX[0] = new FloatBufferRepresentation(data, 0, graphY[0].size);

        //Call the full addGraphData with the artificial x data
        addGraphData(graphY, min, max, graphX, 0, graphY[0].size-1, Double.NaN, Double.NaN, new ArrayList[graphY.length], new ArrayList[graphY.length]);
    }

    public String getUnitX() {
        return unitX;
    }

    public String getUnitY() {
        return unitY;
    }

    public String getUnitZ() {
        return unitZ;
    }

    public String getUnitYX() {
        return unitYX;
    }

    public String getLabelAndUnitX() {
        if (unitX != null && !unitX.isEmpty())
            return labelX +  " (" + unitX + ")";
        else
            return labelX;
    }

    public String getLabelAndSystemTimeRangeX(double min, double max, double systemTimeOffset) {
        if (timeReferencesX != null && timeReferencesX.length > 0 && timeReferencesX[0].size() > 0 && !Double.isNaN(min) && !Double.isNaN(max)) {
            int offset = TimeZone.getDefault().getRawOffset();
            int hours = offset / (60*60*1000);
            int minutes = (Math.abs(offset) / (60*1000)) % 60;
            String offsetStr = String.format(Locale.US, "%+d:%02d", hours, minutes);
            return labelX + " (UTC" + offsetStr + ")";
        }else
            return labelX;
    }

    public String getLabelAndUnitY() {
        if (unitY != null && !unitY.isEmpty())
            return labelY +  " (" + unitY + ")";
        else
            return labelY;
    }

    public String getLabelAndSystemTimeRangeY(double min, double max, double systemTimeOffset) {
        if (timeReferencesY != null && timeReferencesY.length > 0 && timeReferencesY[0].size() > 0 && !Double.isNaN(min) && !Double.isNaN(max)) {
            int offset = TimeZone.getDefault().getRawOffset();
            int hours = offset / (60*60*1000);
            int minutes = (Math.abs(offset) / (60*1000)) % 60;
            String offsetStr = String.format(Locale.US, "%+d:%02d", hours, minutes);
            return labelY + " (UTC" + offsetStr + ")";
        } else
            return labelY;
    }

    public String getLabelAndUnitZ() {
        if (unitZ != null && !unitZ.isEmpty())
            return labelZ +  " (" + unitZ + ")";
        else
            return labelZ;
    }

    //Interface to set axis labels
    public void setLabel(String labelX, String labelY, String labelZ, String unitX, String unitY, String unitZ, String unitYX) {
        this.labelX = labelX;
        this.labelY = labelY;
        this.labelZ = labelZ;
        this.unitX = unitX;
        this.unitY = unitY;
        this.unitZ = unitZ;
        this.unitYX = unitYX;
    }

    //Interface to define a time axis
    public void setTimeAxes(boolean timeOnX, boolean timeOnY) {
        this.timeOnX = timeOnX;
        this.timeOnY = timeOnY;
        graphSetup.timeOnX = timeOnX;
        graphSetup.timeOnY = timeOnY;
    }

    //Interface to switch time axis to system time
    public void setAbsoluteTime(boolean absoluteTime) {
        if (style[0] == Style.mapXY)
            return;
        this.absoluteTime = absoluteTime;
        graphSetup.absoluteTime = absoluteTime;
        this.rescale();
        this.invalidate();
    }

    //Interface to switch the internal time data to a timestamp since 1970
    public void setLinearTime(boolean linearTime) {
        this.linearTime = linearTime;
        graphSetup.linearTime = linearTime;
        this.rescale();
        this.invalidate();
    }

    //Interface to select if the red time markers should be visible
    public void setHideTimeMarkers(boolean hideTimeMarkers) {
        this.hideTimeMarkers = hideTimeMarkers;
        graphSetup.hideTimeMarkers = hideTimeMarkers;
        this.invalidate();
    }

    //Interface to configure logarithmic scales
    public void setLogScale(boolean logX, boolean logY, boolean logZ) {
        this.logX = logX;
        this.logY = logY;
        this.logZ = logZ;
        graphSetup.logX = logX;
        graphSetup.logY = logY;
        graphSetup.logZ = logZ;
    }

    public void setPrecision(int xPrecision, int yPrecision, int zPrecision) {
        this.xPrecision = xPrecision;
        this.yPrecision = yPrecision;
        this.zPrecision = zPrecision;
    }

    private double getTimeStepFromRange(double range, double maxTics) {
        int baseUnit;
        if (range < 60) {
            baseUnit = 1; //seconds
        } else if (range < 60*60) {
            baseUnit = 60; //Minutes
        } else if (range < 24*60*60) {
            baseUnit = 60*60; //Hours
        } else {
            baseUnit = 24*60*60; //Days
        }

        double steps = range/baseUnit; //How many steps would there be with step times baseUnit?
        double step;

        //Depending on how many steps we would have, increase or decrease the step in "natural" factors to stay within maxTics
        if (steps * 12 <= maxTics)
            step = baseUnit / 12.0; //day steps => 2 hour steps, hour steps => 5 minute steps, minute steps => 5 second steps, second steps should not match here within reasonable tic numbers for a 60 second range
        else if (steps * 6 <= maxTics)
            step = baseUnit / 6.0; //day steps => 4 hour steps, hour steps => 10 minute steps, minute steps => 10 second steps, second steps should not match here within reasonable tic numbers for a 60 second range
        else if (steps * 4 <= maxTics)
            step = baseUnit / 4.0; //day steps => 6 hour steps, hour steps => 15 minute steps, minute steps => 15 second steps, second steps should not match here within reasonable tic numbers for a 60 second range
        else if (steps * 2 <= maxTics)
            step = baseUnit / 2.0; //day steps => 12 hour steps, hour steps => 30 minute steps, minute steps => 30 second steps, second steps should not match here within reasonable tic numbers for a 60 second range
        else if (steps <= maxTics)
            step = baseUnit; //should not match here within reasonable tic numbers for a 60 second range
        else if (steps <= maxTics * 2.0)
            step = baseUnit * 2.0; //day steps => 2 day steps, hour steps => 2 hour steps, minute steps => 2 minute steps, second steps => 2 second steps
        else if (steps <= maxTics * 5.0)
            step = baseUnit * 5.0; //day steps => 5 day steps, hour steps => 5 hour steps, minute steps => 5 minute steps, second steps => 5 second steps
        else if (steps <= maxTics * 10.0)
            step = baseUnit * 10.0; //day steps => 10 day steps, hour steps => 10 hour steps, minute steps => 10 minute steps, second steps => 10 second steps - usually the next unit should kick in here...
        else if (steps <= maxTics * 20.0)
            step = baseUnit * 20.0; //day steps => 10 day steps, hour steps => 10 hour steps, minute steps => 10 minute steps, second steps => 10 second steps - usually the next unit should kick in here...
        else if (steps <= maxTics * 50.0)
            step = baseUnit * 50.0; //day steps => 10 day steps, hour steps => 10 hour steps, minute steps => 10 minute steps, second steps => 10 second steps - usually the next unit should kick in here...
        else if (steps <= maxTics * 100.0)
            step = baseUnit * 100.0; //day steps => 10 day steps, hour steps => 10 hour steps, minute steps => 10 minute steps, second steps => 10 second steps - usually the next unit should kick in here...
        else if (steps <= maxTics * 200.0)
            step = baseUnit * 200.0; //day steps => 10 day steps, hour steps => 10 hour steps, minute steps => 10 minute steps, second steps => 10 second steps - usually the next unit should kick in here...
        else if (steps <= maxTics * 500.0)
            step = baseUnit * 500.0; //day steps => 10 day steps, hour steps => 10 hour steps, minute steps => 10 minute steps, second steps => 10 second steps - usually the next unit should kick in here...
        else
            step = baseUnit * 1000.0; //day steps => 10 day steps, hour steps => 10 hour steps, minute steps => 10 minute steps, second steps => 10 second steps - usually the next unit should kick in here...
        if (step < 1.0)
            step = 1.0;
        return step;
    }

    //Helper function that figures out where to put tics on an axis
    //Takes the min and max of that axis, a maximum count of tics, whether the axis is supposed to
    // be logarithmic and whether it is showing experiment time or system time (starting at the
    // given offset timestamp since 1970 in milliseconds if non-zero)
    private Tic[] getTics(double min, double max, int maxTics, boolean log, boolean isTime, double systemTimeOffset){
        if (!(max > min))
            return new Tic[0]; //Invalid axis. No tics
        if (Double.isInfinite(min) || Double.isNaN(min) || Double.isInfinite(max) || Double.isNaN(max))
            return new Tic[0];  //Invalid axis. No tics
        if (log) { //Logarithmic axis. This needs logic of its own... We do not consider time here - wouldn't make sense.
            if (min < 0) //negative values do not work for logarithmic axes
                return new Tic[0];
            double logMax = Math.log10(max); //Store the log of the max and min value
            double logMin = Math.log10(min);

            int digitRange = (int)(Math.ceil(logMax)-Math.floor(logMin)); //The range of the axis in terms of the number of digits its labels have (well, actually it's the exponent, you know what I mean..?)

            //we will just set up tics at powers of ten: 0.1, 1, 10, 100 etc.
            if (digitRange < 1) //Range to short for this naive tic algorithm
                return new Tic[0];

            int precision = -(int)Math.floor(logMin);
            double first = Math.pow(10, Math.floor(logMin)); //The first tic above min
            Tic[] tics;  //The array to hold the tics

            if (digitRange < 3) { //Very small range - we will multiple steps inbetween: 1 2 5 10 20 50 100 200 500 ...
                tics = new Tic[3 * digitRange];
                for (int i = 0; i < digitRange; i++) {
                    tics[3 * i] = new Tic(first, precision);
                    tics[3 * i + 1] = new Tic(2 * first, precision);
                    tics[3 * i + 2] = new Tic(5 * first, precision);
                    first *= 10;
                    precision -= 1;
                }
            } else if (digitRange < 4) { //Small range - we will have a step inbetween: 1 5 10 50 100 500 ...
                tics =  new Tic[2*digitRange];
                for (int i = 0; i < digitRange; i++) {
                    tics[2*i] = new Tic(first, precision);
                    tics[2*i+1] = new Tic(5*first, precision);
                    first *= 10;
                    precision -= 1;
                }
            } else { //Regular range - we will do powers of ten
                int magStep = 1; //If we cover huge scales we might want to do larger steps...
                while (digitRange > maxTics * magStep) //Do we have more than max tics? Increase step size then.
                    magStep++;
                double magFactor = Math.pow(10, magStep);
                tics = new Tic[digitRange/magStep+1];
                for (int i = 0; i <= digitRange / magStep; i++) { //Fill the array with powers of ten
                    tics[i] = new Tic(first, precision);
                    first *= magFactor;
                    precision -= magStep;
                }
            }

            return tics; //Done
        }

        //Basic non-logarithmic algorithm
        int precision = 0;
        double range = max-min; //axis range
        double step = 1.; //The step size to be determined.
        if (isTime && systemTimeOffset > 0) {
            step = getTimeStepFromRange(range, maxTics);
        } else {
            int exponent = (int)Math.floor(Math.log10(range)) - 1;
            double stepFactor = Math.pow(10, exponent); //First estimate how large the steps between our tics should be as a power of ten
            double steps = range/stepFactor; //How many steps would there be with step times stepfactor?

            //Depending on how many steps we would have, increase the step factor to stay within maxTics
            if (steps <= maxTics) {
                step = 1 * stepFactor;
                precision = -exponent;
            } else if (steps <= maxTics * 2) {
                step = 2*stepFactor;
                precision = -exponent;
            } else if (steps <= maxTics * 5) {
                step = 5*stepFactor;
                precision = -exponent;
            } else if (steps <= maxTics * 10) {
                step = 10*stepFactor;
                precision = -exponent-1;
            } else if (steps <= maxTics * 20) {
                step = 20*stepFactor;
                precision = -exponent-1;
            } else if (steps <= maxTics * 50) {
                step = 50*stepFactor;
                precision = -exponent-1;
            } else if (steps <= maxTics * 100) {
                step = 100 * stepFactor;
                precision = -exponent-2;
            }
        }

        //ok how many (integer) steps exactly?
        int iSteps = (int)Math.ceil(range/step);

        double first;
        if (systemTimeOffset > 0) {
            double alignedOffset = systemTimeOffset + TimeZone.getDefault().getRawOffset()/1000.0; //DateFormat takes care of the time zone, but we still want to align the tics with the local time to get the tics on 00:00, 12:00 etc instead of 01:00, 13:00 etc.
            first = Math.ceil((alignedOffset + min) / step) * step - alignedOffset; //Value of the first tic
        } else
            first = Math.ceil((min)/step)*step; //Value of the first tic
        Tic[] tics = new Tic[iSteps]; //Array to hold the tics

        //Generate the tics by stepping up from the first tic
        for (int i = 0; i < iSteps; i++) {
            tics[i] = new Tic(first + i * step, precision);
        }

        return tics; //Done
    }

    private String formatTic(Tic tic, int precision, boolean isTime, double systemTimeOffset) {
        if (isTime && systemTimeOffset > 0) {
            double alignedOffset = systemTimeOffset + TimeZone.getDefault().getRawOffset()/1000.0;
            if (Math.abs(Math.round(tic.value + alignedOffset) % (24*60*60)) < 0.0001) //If the time stamp is on 00:00:00, we show the date instead
                return DateFormat.getDateInstance().format(new Date((long)((systemTimeOffset + tic.value) * 1000)));
            else
                return DateFormat.getTimeInstance().format(new Date((long)((systemTimeOffset + tic.value) * 1000)));
        }

        if (precision < 0 && (tic.value == 0 || ((Math.abs(tic.value) < 10000) && (Math.abs(tic.value) >= 0.0001))))
            return String.format("%." + Math.max(tic.precision, 0) + "f", tic.value);
        else {
            try {
                return String.format("%." + (precision < 0 ? 3 : precision) + "g", tic.value);
            } catch (ArrayIndexOutOfBoundsException e) {
                //Workaround for Java bug https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6469160 as occuring for example on Samsung Galaxy S6
                return String.format("%." + (precision < 0 ? 3 : precision) + "f", tic.value);
            }
        }
    }

    public double dataXToViewX(double dx) {
        if (logX)
            return  (Math.log(dx/graphSetup.minX))/(Math.log(graphSetup.maxX/graphSetup.minX))*(graphSetup.plotBoundW-1)+graphSetup.plotBoundL;
        else
            return (dx-graphSetup.minX)/(graphSetup.maxX-graphSetup.minX)*(graphSetup.plotBoundW-1)+graphSetup.plotBoundL;
    }

    public double dataYToViewY(double dy) {
        if (logY)
            return graphSetup.plotBoundH+graphSetup.plotBoundT-(Math.log(dy/graphSetup.minY))/(Math.log(graphSetup.maxY/graphSetup.minY))*(graphSetup.plotBoundH-1);
        else
            return graphSetup.plotBoundH+graphSetup.plotBoundT-(dy-graphSetup.minY)/(graphSetup.maxY-graphSetup.minY)*(graphSetup.plotBoundH-1);
    }

    public double dataZToViewX(double dz) {
        if (logZ)
            return  (Math.log(dz/graphSetup.minZ))/(Math.log(graphSetup.maxZ/graphSetup.minZ))*(graphSetup.zaBoundW-1)+graphSetup.zaBoundL;
        else
            return (dz-graphSetup.minZ)/(graphSetup.maxZ-graphSetup.minZ)*(graphSetup.zaBoundW-1)+graphSetup.zaBoundL;
    }

    public double viewXToDataX(double vx) {
        if (logX)
            return Math.pow((graphSetup.maxX/graphSetup.minX),(vx-graphSetup.plotBoundL)/(graphSetup.plotBoundW-1))*graphSetup.minX;
        else
            return graphSetup.minX+(vx-graphSetup.plotBoundL)*(graphSetup.maxX-graphSetup.minX)/(graphSetup.plotBoundW-1);
    }

    public double viewYToDataY(double vy) {
        if (logY)
            return Math.pow((graphSetup.maxY/graphSetup.minY),(graphSetup.plotBoundH+graphSetup.plotBoundT-vy)/(graphSetup.plotBoundH-1))*graphSetup.minY;
        else
            return graphSetup.minY + (graphSetup.plotBoundH+graphSetup.plotBoundT-vy)*(graphSetup.maxY-graphSetup.minY)/(graphSetup.plotBoundH-1);
    }

    public double viewXToDataZ(double vx) {
        if (logZ)
            return Math.pow((graphSetup.maxZ/graphSetup.minZ),(vx-graphSetup.zaBoundL)/(graphSetup.zaBoundW-1))*graphSetup.minZ;
        else
            return graphSetup.minZ+(vx-graphSetup.zaBoundL)*(graphSetup.maxZ-graphSetup.minZ)/(graphSetup.zaBoundW-1);
    }

    private double offsetFromExperimentTime(double v) {
        if (absoluteTime && !linearTime) {
            int i = 0;
            while (i + 1 < graphSetup.trStarts.size() && graphSetup.trStarts.get(i + 1) < v)
                i++;
            return graphSetup.systemTimeReferenceGap.get(i);
        } else if (linearTime && !absoluteTime) {
            int i = 0;
            while (i + 1 < graphSetup.trStarts.size() && graphSetup.trStarts.get(i + 1) + graphSetup.systemTimeReferenceGap.get(i+1) < v)
                i++;
            return -graphSetup.systemTimeReferenceGap.get(i);
        }
        return 0.0;
    }

    private double offsetFromSystemTime(double v) {
        if (absoluteTime && !linearTime) {
            int i = 0;
            while (i + 1 < graphSetup.trStarts.size() && i + 1 < graphSetup.systemTimeReferenceGap.size() && graphSetup.trStarts.get(i + 1) + graphSetup.systemTimeReferenceGap.get(i + 1) < v)
                i++;
            return graphSetup.systemTimeReferenceGap.get(i);
        } else if (linearTime && !absoluteTime) {
            int i = 0;
            while (i + 1 < graphSetup.trStarts.size() && i + 1 < graphSetup.systemTimeReferenceGap.size() && graphSetup.trStarts.get(i + 1) < v)
                i++;
            return -graphSetup.systemTimeReferenceGap.get(i);
        }
        return 0.0;
    }

    @Override
    //Draw the graph!
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Resources res = getResources();

        paint.setTextSize(Helper.getUserSelectedGraphSetting(getContext(), Helper.GraphField.TEXT_SIZE));
        paint.setColor(res.getColor(R.color.phyphox_white_100));
        paint.setStrokeWidth(1);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);

        double workingMinX = Double.isNaN(zoomState.minX) ? minX : zoomState.minX;
        double workingMaxX = Double.isNaN(zoomState.maxX) ? maxX : zoomState.maxX;
        double workingMinY = Double.isNaN(zoomState.minY) ? minY : zoomState.minY;
        double workingMaxY = Double.isNaN(zoomState.maxY) ? maxY : zoomState.maxY;
        double workingMinZ = Double.isNaN(zoomState.minZ) ? minZ : zoomState.minZ;
        double workingMaxZ = Double.isNaN(zoomState.maxZ) ? maxZ : zoomState.maxZ;;

        //Do we need a zscale?
        boolean zScale = false;
        for (int i = 0; i < style.length; i++) {
            if (style[i] == Style.mapXY)
                zScale = true;
        }

        //Stretch x slightly to give a little headroom... Also force a range if it is zero
        if (!logX && !zScale && !timeOnX) {
            double extraX = (workingMaxX - workingMinX) * 0.05;
            if (extraX == 0)
                extraX = workingMaxX * 0.05;
            workingMaxX += extraX;
            workingMinX -= extraX;
        }

        //Stretch y slightly to give a little headroom... Also force a range if it is zero
        if (!logY && !zScale && !timeOnY) {
            double extraY = (workingMaxY - workingMinY) * 0.05;
            if (extraY == 0)
                extraY = workingMaxY * 0.05;
            workingMaxY += extraY;
            workingMinY -= extraY;
        }

        //Time axis should auto-extend to the actually measured time range
        if (timeOnX && !linearTime && Double.isNaN(zoomState.minX) && Double.isNaN(zoomState.maxX) && scaleMinX == scaleMode.auto && scaleMaxX == scaleMode.auto) {
            if (graphSetup.trStarts != null && graphSetup.trStarts.size() > 0 && graphSetup.trStarts.get(0) < workingMinX)
                workingMinX = graphSetup.trStarts.get(0);
            if (graphSetup.trStops != null && graphSetup.trStops.size() > 0 && graphSetup.trStops.get(graphSetup.trStops.size()-1) > workingMaxX)
                workingMaxX = graphSetup.trStops.get(graphSetup.trStops.size()-1);
        }
        if (timeOnY && !linearTime && Double.isNaN(zoomState.minY) && Double.isNaN(zoomState.maxY) && scaleMinY == scaleMode.auto && scaleMaxY == scaleMode.auto) {
            if (graphSetup.trStarts != null && graphSetup.trStarts.size() > 0 && graphSetup.trStarts.get(0) < workingMinY)
                workingMinY = graphSetup.trStarts.get(0);
            if (graphSetup.trStops != null && graphSetup.trStops.size() > 0 && graphSetup.trStops.get(graphSetup.trStops.size()-1) > workingMaxY)
                workingMaxY = graphSetup.trStops.get(graphSetup.trStops.size()-1);
        }

        //On log scales zero is a problem. We just set a minimum which works for most plots.
        //However, this is a compromise.
        if (logX && workingMinX < 0.000001)
            workingMinX = 0.000001;
        if (logY && workingMinY < 0.000001)
            workingMinY = 0.000001;
        if (logZ && workingMinZ < 0.000001)
            workingMinZ = 0.000001;

        double systemTimeOffsetX, systemTimeOffsetY;
        if (absoluteTime) {
            systemTimeOffsetX = (timeOnX && timeReferencesX != null && timeReferencesX.length > 0 && timeReferencesX[0].size() > 0) ? (timeReferencesX[0].get(0).systemTime*0.001 - timeReferencesX[0].get(0).experimentTime) : 0.0;
            systemTimeOffsetY = (timeOnY && timeReferencesY != null && timeReferencesY.length > 0 && timeReferencesY[0].size() > 0) ? (timeReferencesY[0].get(0).systemTime*0.001 - timeReferencesY[0].get(0).experimentTime) : 0.0;
        } else {
            systemTimeOffsetX = 0.0;
            systemTimeOffsetY = 0.0;
        }

        int maxXTics = timeOnX && absoluteTime ? maxXTimeTics : maxXRegularTics;
        int maxYTics = timeOnY && absoluteTime ? maxYTimeTics : maxYRegularTics;
        int maxZTics = maxZRegularTics;

        //Generate the tics
        Tic[] xTics = getTics(workingMinX, workingMaxX, maxXTics, logX, timeOnX, systemTimeOffsetX);
        Tic[] yTics = getTics(workingMinY, workingMaxY, maxYTics, logY, timeOnY, systemTimeOffsetY);
        Tic[] zTics = null;
        if (zScale)
            zTics = getTics(workingMinZ, workingMaxZ, maxZTics, logZ, false, 0);

        //Calculate area...
        int w = this.getWidth();
        int h = this.getHeight();

        //Consider room for the labels and the tics
        int graphB = (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.3);
        if (labelX != null)
            graphB += (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.3);
        int graphL = 0;
        int graphT = 0;
        for (Tic tic : yTics) {
            double tw = paint.measureText(formatTic(tic, yPrecision, timeOnY, systemTimeOffsetY)) + res.getDimension(R.dimen.graph_font) / 2.;
            if (tw > graphL)
                graphL = (int)Math.ceil(tw);
        }
        if (labelY != null)
            graphL += (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.2);

        int graphW = w-graphL;
        int graphH = h-graphB;

        //Calculate space for z scale if necessary
        int zScaleH = 0;
        if (zScale) {
            zScaleH = (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.2);
            graphT += 3.5*zScaleH;
            graphH -= graphT;
        }

        //Report axis ranges to graph
        graphSetup.setDataBounds((float)workingMinX, (float)workingMaxX, (float)workingMinY, (float)workingMaxY, (float)workingMinZ, (float)workingMaxZ);
        graphSetup.setPlotBounds(graphL, graphT, graphW, graphH);
        graphSetup.setZAxisBounds(graphL, 0, graphW, zScaleH);
        graphSetup.setTics(xTics, yTics, zTics, plotRenderer);
        plotRenderer.notifyUpdateTimeRanges();
        if (zTics != null)
            for (int i = 0; i < zTics.length; i++)

        //Labels for the tics
        paint.setTextAlign(Paint.Align.CENTER);
        for (Tic tic : xTics) {
            if (tic.value < workingMinX || tic.value > workingMaxX)
                continue;
            double x = dataXToViewX(tic.value);
            canvas.drawText(formatTic(tic, xPrecision, timeOnX, systemTimeOffsetX), (float)x, h-graphB+(float)(res.getDimensionPixelSize(R.dimen.graph_font)*1.1), paint);
        }
        paint.setTextAlign(Paint.Align.RIGHT);
        for (Tic tic : yTics) {
            if (tic.value < workingMinY || tic.value > workingMaxY)
                continue;
            double y = dataYToViewY(tic.value);
            canvas.drawText(formatTic(tic, yPrecision, timeOnY, systemTimeOffsetY), graphL-(float)(res.getDimensionPixelSize(R.dimen.graph_font)*0.2), (float)(y+(res.getDimensionPixelSize(R.dimen.graph_font)*0.4)), paint);
        }
        if (zScale) {
            paint.setTextAlign(Paint.Align.CENTER);
            for (Tic tic : zTics) {
                if (tic.value < workingMinZ || tic.value > workingMaxZ)
                    continue;
                double x = dataZToViewX(tic.value);
                canvas.drawText(formatTic(tic, zPrecision, false, 0), (float)x, zScaleH+(float)(res.getDimensionPixelSize(R.dimen.graph_font)*1.1), paint);
            }
        }

        //Labels
        paint.setTextAlign(Paint.Align.CENTER);
        if (labelX != null)
            canvas.drawText(timeOnX && absoluteTime ? getLabelAndSystemTimeRangeX(workingMinX, workingMaxX, systemTimeOffsetX) : getLabelAndUnitX(), graphL+graphW/2, h-(int)(res.getDimensionPixelSize(R.dimen.graph_font)*0.3), paint);
        if (labelY != null) {
            canvas.save();
            canvas.rotate(-90, res.getDimensionPixelSize(R.dimen.graph_font), graphH / 2 + graphT);
            canvas.drawText(timeOnY && absoluteTime ? getLabelAndSystemTimeRangeY(workingMinY, workingMaxY, systemTimeOffsetY) : getLabelAndUnitY(), res.getDimensionPixelSize(R.dimen.graph_font), graphH / 2 + graphT, paint);
            canvas.restore();
        }
        if (zScale && labelZ != null) {
            canvas.drawText(getLabelAndUnitZ(), graphL+graphW/2, zScaleH+(int)(res.getDimensionPixelSize(R.dimen.graph_font)*2.4), paint);
        }

        //Draw rect around graph
        paint.setColor(res.getColor(R.color.phyphox_white_100));

        paint.setStrokeWidth(Helper.getUserSelectedGraphSetting(getContext(), Helper.GraphField.BORDER_WIDTH));
        paint.setAlpha(255);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(graphL + 1, graphT+1, w - 1, h - graphB - 1, paint);
        if (zScale)
            canvas.drawRect(graphL + 1, 1, w - 1, zScaleH - 1, paint);

        //Update the marker if a datapoint has been selected
        for (int i = 0; i < maxPicked; i++) {
            if (pickedPointIndex[i] >= 0) {
                double xi, yi, zi, vxi, vyi, d;
                try {
                    xi = graphSetup.dataSets.get(pickedPointGraphIndex[i]).fbX.data.get(graphSetup.dataSets.get(pickedPointGraphIndex[i]).fbX.offset + pickedPointIndex[i]);
                    yi = graphSetup.dataSets.get(pickedPointGraphIndex[i]).fbY.data.get(graphSetup.dataSets.get(pickedPointGraphIndex[i]).fbX.offset + pickedPointIndex[i]);

                    if (graphSetup.dataSets.size() > pickedPointGraphIndex[i]+1 && graphSetup.dataSets.get(pickedPointGraphIndex[i]+1).style == Style.mapZ) {
                        zi = graphSetup.dataSets.get(pickedPointGraphIndex[i]+1).fbY.data.get(graphSetup.dataSets.get(pickedPointGraphIndex[i]+1).fbY.offset + pickedPointIndex[i]);
                    } else
                        zi = Double.NaN;

                    if (timeOnX)
                        xi += offsetFromExperimentTime(xi);
                    if (timeOnY)
                        yi += offsetFromExperimentTime(yi);

                    vxi = dataXToViewX(xi);
                    vyi = dataYToViewY(yi);

                    pointInfoListener.showPointInfo((float) vxi, (float) vyi, (float) xi, (float) yi, (float) zi, i);
                } catch (Exception e) {
                    pointInfoListener.hidePointInfo(i);
                }
            } else {
                pointInfoListener.hidePointInfo(i);
            }
        }


        //Draw graph in OpenGL
        plotRenderer.requestRender();

    }
}

