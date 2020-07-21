package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Vector;

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

    private double aspectRatio = 3.;

    private int historyLength; //If set to n > 1 the graph will also show the last n sets in a different color
    private int nCurves; //Tracks the number of entries in the history

    Style[] style; //Styles for each graph
    int[] mapWidth; //MapWidth for each graph (if applicable)
    Vector<Integer> colorScale = new Vector<>();

    private final static int maxXTics = 5; //Constant to set a target number of tics on the x axis
    private final static int maxYTics = 5; //Constant to set a target number of tics on the y axis
    private final static int maxZTics = 5; //Constant to set a target number of tics on the y axis
    private String labelX = null; //Label for the x-axis
    private String labelY = null; //Label for the y-axis
    private String labelZ = null; //Label for the y-axis
    private String unitX = null; //Label for the x-axis
    private String unitY = null; //Label for the y-axis
    private String unitZ = null; //Label for the y-axis
    private String unitYX = null; //Unit for the slope, i.e. y/x
    public boolean logX = false; //logarithmic scale for the x-axis?
    public boolean logY = false; //logarithmic scale for the y-axis?
    public boolean logZ = false; //logarithmic scale for the y-axis?
    private int xPrecision = 3;
    private int yPrecision = 3;
    private int zPrecision = 3;

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
                zoomState.minZ = viewXToDataZ(dataZToViewX(scaleXOrigin) - (dataZToViewX(scaleXOrigin) - dataZToViewX(scaleXStartMin)) / scaleX + scaleXStart - focusX);
                zoomState.maxZ = viewXToDataZ(dataZToViewX(scaleXOrigin) - (dataZToViewX(scaleXOrigin) - dataZToViewX(scaleXStartMax)) / scaleX + scaleXStart - focusX);
            } else {
                zoomState.minX = viewXToDataX(dataXToViewX(scaleXOrigin) - (dataXToViewX(scaleXOrigin) - dataXToViewX(scaleXStartMin)) / scaleX + scaleXStart - focusX);
                zoomState.minY = viewYToDataY(dataYToViewY(scaleYOrigin) - (dataYToViewY(scaleYOrigin) - dataYToViewY(scaleYStartMin)) / scaleY + scaleYStart - focusY);
                zoomState.maxX = viewXToDataX(dataXToViewX(scaleXOrigin) - (dataXToViewX(scaleXOrigin) - dataXToViewX(scaleXStartMax)) / scaleX + scaleXStart - focusX);
                zoomState.maxY = viewYToDataY(dataYToViewY(scaleYOrigin) - (dataYToViewY(scaleYOrigin) - dataYToViewY(scaleYStartMax)) / scaleY + scaleYStart - focusY);
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
                vxi = dataXToViewX(xi[offX + j]);
                vyi = dataYToViewY(yi[offY + j]);
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
                    zoomState.minZ = viewXToDataZ(dataZToViewX(panXOrigin) - dx);
                    zoomState.maxZ = viewXToDataZ(dataZToViewX(panXOrigin + panWOrigin) - dx);
                } else {
                    zoomState.minX = viewXToDataX(dataXToViewX(panXOrigin) - dx);
                    zoomState.minY = viewYToDataY(dataYToViewY(panYOrigin) - dy);
                    zoomState.maxX = viewXToDataX(dataXToViewX(panXOrigin + panWOrigin) - dx);
                    zoomState.maxY = viewYToDataY(dataYToViewY(panYOrigin + panHOrigin) - dy);
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
            if (zoomState.follows) {
                double w = zoomState.maxX - zoomState.minX;
                zoomState.minX = dataMaxX - w;
                zoomState.maxX = dataMaxX;
            }
        }
    }

    //Add a new graph and (if enabled) push the old graphs back into history
    public void addGraphData(FloatBufferRepresentation[] graphY, double minY, double maxY, FloatBufferRepresentation[] graphX, double minX, double maxX, double minZ, double maxZ) {
        if (graphY == null || graphX == null || graphX[0] == null || graphY[0] == null)
            return;

        if (historyLength > 1) {
            //Push back data in the history
            for (int i = nCurves - 1; i > 0; i--) {
                this.graphY[i] = this.graphY[i - 1];
                this.graphX[i] = this.graphX[i - 1];
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
            if (i < graphX.length)
                this.graphX[i] = graphX[i];
            else
                this.graphX[i] = null;
        }
        this.histMinX[0] = minX;
        this.histMaxX[0] = maxX;
        this.histMinY[0] = minY;
        this.histMaxY[0] = maxY;
        this.histMinZ = minZ;
        this.histMaxZ = maxZ;

        graphSetup.setData(this.graphX, this.graphY, nCurves, style, mapWidth, plotRenderer);

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
            addGraphData(graphY, min, max, graphY, min, max, min, max);
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
        addGraphData(graphY, min, max, graphX, 0, graphY[0].size-1, Double.NaN, Double.NaN);
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

    public String getLabelAndUnitY() {
        if (unitY != null && !unitY.isEmpty())
            return labelY +  " (" + unitY + ")";
        else
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

    //Helper function that figures out where to put tics on an axis
    //Takes the min and max of that axis, a maximum count of tics and whether the axis is supposed
    // to be logarithmic
    private double[] getTics(double min, double max, int maxTics, boolean log){
        if (!(max > min))
            return new double[0]; //Invalid axis. No tics
        if (Double.isInfinite(min) || Double.isNaN(min) || Double.isInfinite(max) || Double.isNaN(max))
            return new double[0];  //Invalid axis. No tics
        if (log) { //Logarithmic axis. This needs logic of its own...
            if (min < 0) //negative values do not work for logarithmic axes
                return new double[0];
            double logMax = Math.log10(max); //Store the log of the max and min value
            double logMin = Math.log10(min);

            int digitRange = (int)(Math.ceil(logMax)-Math.floor(logMin)); //The range of the axis in terms of the number of digits its labels have (well, actually it's the exponent, you know what I mean..?)

            //we will just set up tics at powers of ten: 0.1, 1, 10, 100 etc.
            if (digitRange < 1) //Range to short for this naive tic algorithm
                return new double[0];

            double first = Math.pow(10, Math.floor(logMin)); //The first tic above min
            double[] tics;  //The array to hold the tics

            if (digitRange < 3) { //Very small range - we will multiple steps inbetween: 1 2 5 10 20 50 100 200 500 ...
                tics = new double[3 * digitRange];
                for (int i = 0; i < digitRange; i++) {
                    tics[3 * i] = first;
                    tics[3 * i + 1] = 2 * first;
                    tics[3 * i + 2] = 5 * first;
                    first *= 10;
                }
            } else if (digitRange < 4) { //Small range - we will have a step inbetween: 1 5 10 50 100 500 ...
                tics =  new double[2*digitRange];
                for (int i = 0; i < digitRange; i++) {
                    tics[2*i] = first;
                    tics[2*i+1] = 5*first;
                    first *= 10;
                }
            } else { //Regular range - we will do powers of ten
                int magStep = 1; //If we cover huge scales we might want to do larger steps...
                while (digitRange > maxTics * magStep) //Do we have more than max tics? Increase step size then.
                    magStep++;
                double magFactor = Math.pow(10, magStep);
                tics = new double[digitRange/magStep+1];
                for (int i = 0; i <= digitRange / magStep; i++) { //Fill the array with powers of ten
                    tics[i] = first;
                    first *= magFactor;
                }
            }

            return tics; //Done
        }

        //Basic non-logarithmic algorithm
        double range = max-min; //axis range
        double stepFactor = Math.pow(10,Math.floor(Math.log10(range))-1); //First estimate how large the steps between our tics should be as a power of ten
        double step = 1.; //The finer step size within the power of ten
        double steps = range/stepFactor; //How many steps would there be with step times stepfactor?

        //Depending on how many steps we would have, increase the step factor to stay within maxTics
        if (steps <= maxTics)
            step = 1*stepFactor;
        else if (steps <= maxTics * 2)
            step = 2*stepFactor;
        else if (steps <= maxTics * 5)
            step = 5*stepFactor;
        else if (steps <= maxTics * 10)
            step = 10*stepFactor;
        else if (steps <= maxTics * 20)
            step = 20*stepFactor;
        else if (steps <= maxTics * 50)
            step = 50*stepFactor;
        else if (steps <= maxTics * 100)
            step = 100*stepFactor;

        //ok how many (integer) steps exactly?
        int iSteps = (int)Math.ceil(range/step);

        double first = Math.ceil(min/step)*step; //Value of the first tic
        double[] tics = new double[iSteps]; //Array to hold the tics

        //Generate the tics by stepping up from the first tic
        for (int i = 0; i < iSteps; i++) {
            tics[i] = first + i * step;
        }

        return tics; //Done
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

    @Override
    //Draw the graph!
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Resources res = getResources();

        paint.setTextSize(res.getDimension(R.dimen.graph_font));
        paint.setColor(res.getColor(R.color.mainExp));
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
        if (!logX && !zScale) {
            double extraX = (workingMaxX - workingMinX) * 0.05;
            if (extraX == 0)
                extraX = workingMaxX * 0.05;
            workingMaxX += extraX;
            workingMinX -= extraX;
        }

        //Stretch y slightly to give a little headroom... Also force a range if it is zero
        if (!logY && !zScale) {
            double extraY = (workingMaxY - workingMinY) * 0.05;
            if (extraY == 0)
                extraY = workingMaxY * 0.05;
            workingMaxY += extraY;
            workingMinY -= extraY;
        }

        //On log scales zero is a problem. We just set a minimum which works for most plots.
        //However, this is a compromise.
        if (logX && workingMinX < 0.000001)
            workingMinX = 0.000001;
        if (logY && workingMinY < 0.000001)
            workingMinY = 0.000001;
        if (logZ && workingMinZ < 0.000001)
            workingMinZ = 0.000001;

        //Generate the tics
        double[] xTics = getTics(workingMinX, workingMaxX, maxXTics, logX);
        double[] yTics = getTics(workingMinY, workingMaxY, maxYTics, logY);
        double[] zTics = null;
        if (zScale)
            zTics = getTics(workingMinZ, workingMaxZ, maxZTics, logZ);

        //Calculate area...
        int w = this.getWidth();
        int h = this.getHeight();

        //Consider room for the labels and the tics
        int graphB = (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.3);
        if (labelX != null)
            graphB += (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.3);
        int graphL = 0;
        int graphT = 0;
        for (double tic : yTics) {
            double tw;
            try {
                tw = paint.measureText(String.format("%." + yPrecision + "g", tic)) + res.getDimension(R.dimen.graph_font) / 2.;
            } catch (ArrayIndexOutOfBoundsException e) {
                //Workaround for Java bug https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6469160 as occuring for example on Samsung Galaxy S6
                tw = paint.measureText(String.format("%." + yPrecision + "f", tic)) + res.getDimension(R.dimen.graph_font) / 2.;
            }
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
        if (zTics != null)
            for (int i = 0; i < zTics.length; i++)

        //Labels for the tics
        paint.setTextAlign(Paint.Align.CENTER);
        for (double tic : xTics) {
            if (tic < workingMinX || tic > workingMaxX)
                continue;
            double x = dataXToViewX(tic);
            try {
                canvas.drawText(String.format("%."+xPrecision+"g", tic), (float)x, h-graphB+(float)(res.getDimensionPixelSize(R.dimen.graph_font)*1.1), paint);
            } catch (ArrayIndexOutOfBoundsException e) {
                //Workaround for Java bug https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6469160 as occuring for example on Samsung Galaxy S6
                canvas.drawText(String.format("%."+xPrecision+"f", tic), (float)x, h-graphB+(float)(res.getDimensionPixelSize(R.dimen.graph_font)*1.1), paint);
            }
        }
        paint.setTextAlign(Paint.Align.RIGHT);
        for (double tic : yTics) {
            if (tic < workingMinY || tic > workingMaxY)
                continue;
            double y = dataYToViewY(tic);
            try {
                canvas.drawText(String.format("%."+yPrecision+"g", tic), graphL-(float)(res.getDimensionPixelSize(R.dimen.graph_font)*0.2), (float)(y+(res.getDimensionPixelSize(R.dimen.graph_font)*0.4)), paint);
            } catch (ArrayIndexOutOfBoundsException e) {
                //Workaround for Java bug https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6469160 as occuring for example on Samsung Galaxy S6
                canvas.drawText(String.format("%."+yPrecision+"f", tic), graphL-(float)(res.getDimensionPixelSize(R.dimen.graph_font)*0.2), (float)(y+(res.getDimensionPixelSize(R.dimen.graph_font)*0.4)), paint);
            }
        }
        if (zScale) {
            paint.setTextAlign(Paint.Align.CENTER);
            for (double tic : zTics) {
                if (tic < workingMinZ || tic > workingMaxZ)
                    continue;
                double x = dataZToViewX(tic);
                try {
                    canvas.drawText(String.format("%."+zPrecision+"g", tic), (float)x, zScaleH+(float)(res.getDimensionPixelSize(R.dimen.graph_font)*1.1), paint);
                } catch (ArrayIndexOutOfBoundsException e) {
                    //Workaround for Java bug https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6469160 as occuring for example on Samsung Galaxy S6
                    canvas.drawText(String.format("%."+zPrecision+"f", tic), (float)x, zScaleH+(float)(res.getDimensionPixelSize(R.dimen.graph_font)*1.1), paint);
                }
            }
        }

        //Labels
        paint.setTextAlign(Paint.Align.CENTER);
        if (labelX != null)
            canvas.drawText(getLabelAndUnitX(), graphL+graphW/2, h-(int)(res.getDimensionPixelSize(R.dimen.graph_font)*0.3), paint);
        if (labelY != null) {
            canvas.save();
            canvas.rotate(-90, res.getDimensionPixelSize(R.dimen.graph_font), graphH / 2 + graphT);
            canvas.drawText(getLabelAndUnitY(), res.getDimensionPixelSize(R.dimen.graph_font), graphH / 2 + graphT, paint);
            canvas.restore();
        }
        if (zScale && labelZ != null) {
            canvas.drawText(getLabelAndUnitZ(), graphL+graphW/2, zScaleH+(int)(res.getDimensionPixelSize(R.dimen.graph_font)*2.4), paint);
        }

        //Draw rect around graph
        paint.setColor(res.getColor(R.color.mainExp));
        paint.setStrokeWidth(3);
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

