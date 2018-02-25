package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ScaleGestureDetectorCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.view.MotionEvent.INVALID_POINTER_ID;

//The graphView class implements an Android view which displays a data graph

public class graphView extends View {
    public enum Style {
        lines, dots, hbars, vbars, unknown;
    }

    public static Style styleFromStr(String str) {
        switch (str) {
            case "lines": return Style.lines;
            case "dots": return Style.dots;
            case "hbars": return Style.hbars;
            case "vbars": return Style.vbars;
        }
        return Style.unknown;
    }

    private floatBufferRepresentation[] graphX; //The x data to be displayed
    private double[] histMinX, histMaxX;
    private floatBufferRepresentation[] graphY; //The y data to be displayed
    private double[] histMinY, histMaxY;

    private double aspectRatio = 3.;

    private int historyLength; //If set to n > 1 the graph will also show the last n sets in a different color
    private int nCurves; //Tracks the number of entries in the history

    Style[] style; //Styles for each graph

    private final static int maxXTics = 6; //Constant to set a target number of tics on the x axis
    private final static int maxYTics = 6; //Constant to set a target number of tics on the y axis
    private String labelX = null; //Label for the x-axis
    private String labelY = null; //Label for the y-axis
    private boolean logX = false; //logarithmic scale for the x-axis?
    private boolean logY = false; //logarithmic scale for the y-axis?
    private int xPrecision = 3;
    private int yPrecision = 3;

    private double[] lineWidth;
    private int[] color;

    public enum scaleMode {
        auto, extend, fixed
    }

    scaleMode scaleMinX = scaleMode.auto;
    scaleMode scaleMaxX = scaleMode.auto;
    scaleMode scaleMinY = scaleMode.auto;
    scaleMode scaleMaxY = scaleMode.auto;

    double minX = 0.;
    double maxX = 0.;
    double minY = 0.;
    double maxY = 0.;

    private boolean allowZooming = false;
    double zoomMinX = Double.NaN;
    double zoomMaxX = Double.NaN;
    double zoomMinY = Double.NaN;
    double zoomMaxY = Double.NaN;

    Paint paint; //Anti-Aliased paint used all over this class

    PlotAreaView plotAreaView;
    PlotRenderer plotRenderer;
    GraphSetup graphSetup;

    private boolean processingGesture = false;
    private float panXStart = Float.NaN;
    private float panYStart = Float.NaN;
    private double panXOrigin = Double.NaN;
    private double panYOrigin = Double.NaN;

    private float scaleXStart = Float.NaN;
    private float scaleYStart = Float.NaN;
    private float scaleXStartSpan = Float.NaN;
    private float scaleYStartSpan = Float.NaN;
    private double scaleXOrigin = Double.NaN;
    private double scaleYOrigin = Double.NaN;
    private double scaleXStartScale = Double.NaN;
    private double scaleYStartScale = Double.NaN;

    private ScaleGestureDetector.OnScaleGestureListener scaleListener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            scaleXStart = scaleGestureDetector.getFocusX();
            scaleYStart = scaleGestureDetector.getFocusY();

            if (!(scaleXStart > graphSetup.plotBoundL && scaleXStart < graphSetup.plotBoundL + graphSetup.plotBoundW && scaleYStart > graphSetup.plotBoundT && scaleYStart < graphSetup.plotBoundT + graphSetup.plotBoundH))
                return false;

            scaleXStartSpan = scaleGestureDetector.getCurrentSpanX();
            scaleYStartSpan = scaleGestureDetector.getCurrentSpanY();

            final double cminX = Double.isNaN(zoomMinX) ? minX : zoomMinX;
            final double cminY = Double.isNaN(zoomMinY) ? minY : zoomMinY;
            final double cmaxX = Double.isNaN(zoomMaxX) ? maxX : zoomMaxX;
            final double cmaxY = Double.isNaN(zoomMaxY) ? maxY : zoomMaxY;

            scaleXStartScale = cmaxX-cminX;
            scaleYStartScale = cmaxY-cminY;
            scaleXOrigin = cminX + (scaleXStart - graphSetup.plotBoundL) / (graphSetup.plotBoundW) * (scaleXStartScale);
            scaleYOrigin = cmaxY - (scaleYStart - graphSetup.plotBoundT) / (graphSetup.plotBoundH) * (scaleYStartScale);

            processingGesture = false;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            final float spanX = scaleGestureDetector.getCurrentSpanX();
            final float spanY = scaleGestureDetector.getCurrentSpanY();
            final float focusX = scaleGestureDetector.getFocusX();
            final float focusY = scaleGestureDetector.getFocusY();

            double scaleX, scaleY;

            if (scaleXStartSpan/scaleYStartSpan > 0.5)
                scaleX = scaleXStartSpan / spanX * scaleXStartScale;
            else
                scaleX = scaleXStartScale;

            if (scaleYStartSpan/scaleXStartSpan > 0.5)
                scaleY = scaleYStartSpan / spanY * scaleYStartScale;
            else
                scaleY = scaleYStartScale;

            if (scaleX > 10*scaleXStartScale)
                scaleX = 10*scaleXStartScale;

            if (scaleY > 10*scaleYStartScale)
                scaleY = 10*scaleYStartScale;

            zoomMinX = scaleXOrigin - (focusX - graphSetup.plotBoundL) / graphSetup.plotBoundW * scaleX;
            zoomMaxX = zoomMinX + scaleX;
            zoomMaxY = scaleYOrigin + (focusY - graphSetup.plotBoundT) / graphSetup.plotBoundH * scaleY;
            zoomMinY = zoomMaxY - scaleY;

            invalidate();

            processingGesture = false;

            return true;
        }

    };
    private ScaleGestureDetector scaleGestureDetector;

    //Simple constructor just needs a context to call the View constructor
    //Initialize some stuff...
    public graphView(Context context, double aspectRatio, PlotAreaView plotAreaView, PlotRenderer plotRenderer) {
        super(context);
        this.aspectRatio = aspectRatio;
        this.plotAreaView = plotAreaView;
        this.plotRenderer = plotRenderer;
        this.graphSetup = new GraphSetup();
        this.plotRenderer.setGraphSetup(this.graphSetup);
        setHistoryLength(1);
        rescale(); //Calculate initial scale. At this point it will just set all min and max to +inf and -inf
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scaleGestureDetector = new ScaleGestureDetector(context, scaleListener);
    }

    public void setAllowZooming(boolean allow) {
        allowZooming = allow;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!allowZooming)
            return super.onTouchEvent(event);

        scaleGestureDetector.onTouchEvent(event);

        if (event.getPointerCount() > 1)
            return true;

        final float x = event.getX();
        final float y = event.getY();

        if (!processingGesture
                && (!(x > graphSetup.plotBoundL && x < graphSetup.plotBoundL + graphSetup.plotBoundW && y > graphSetup.plotBoundT && y < graphSetup.plotBoundT + graphSetup.plotBoundH)))
            return super.onTouchEvent(event);

        final int action = MotionEventCompat.getActionMasked(event);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                panXStart = x;
                panYStart = y;
                panXOrigin = Double.NaN;
                panYOrigin = Double.NaN;
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
                    panXOrigin = Double.isNaN(zoomMinX) ? minX : zoomMinX;
                    panYOrigin = Double.isNaN(zoomMinY) ? minY : zoomMinY;
                }

                double w = (Double.isNaN(zoomMaxX) ? maxX : zoomMaxX) - (Double.isNaN(zoomMinX) ? minX : zoomMinX);
                double h = (Double.isNaN(zoomMaxY) ? maxY : zoomMaxY) - (Double.isNaN(zoomMinY) ? minY : zoomMinY);

                zoomMinX = panXOrigin - dx * w / graphSetup.plotBoundW;
                zoomMinY = panYOrigin + dy * h / graphSetup.plotBoundH;
                zoomMaxX = zoomMinX + w;
                zoomMaxY = zoomMinY + h;

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
        graphX = new floatBufferRepresentation[n];
        graphY = new floatBufferRepresentation[n];
        histMinX = new double[n];
        histMaxX = new double[n];
        histMinY = new double[n];
        histMaxY = new double[n];
        color = new int[n];
        style = new Style[n];
        lineWidth = new double[n];
        graphSetup.initSize(nCurves);
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

    //Rescale any non-fixed ranges
    public void rescale() {
        double dataMinX = Double.POSITIVE_INFINITY;
        double dataMaxX = Double.NEGATIVE_INFINITY;
        double dataMinY = Double.POSITIVE_INFINITY;
        double dataMaxY = Double.NEGATIVE_INFINITY;

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

        if (scaleMinX == scaleMode.auto || (scaleMinX == scaleMode.extend && minX > dataMinX))
            minX = dataMinX;
        if (scaleMaxX == scaleMode.auto || (scaleMaxX == scaleMode.extend && maxX < dataMaxX))
            maxX = dataMaxX;
        if (scaleMinY == scaleMode.auto || (scaleMinY == scaleMode.extend && minY > dataMinY))
            minY = dataMinY;
        if (scaleMaxY == scaleMode.auto || (scaleMaxY == scaleMode.extend && maxY < dataMaxY))
            maxY = dataMaxY;
    }

    //Add a new graph and (if enabled) push the old graphs back into history
    public void addGraphData(floatBufferRepresentation[] graphY, double minY, double maxY, floatBufferRepresentation[] graphX, double minX, double maxX) {
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

        graphSetup.setData(this.graphX, this.graphY, nCurves, plotRenderer);

        //Rescale and invalidate to update everything
        this.rescale();
        this.invalidate();
    }

    //This overloads addGraphData to take pure y-data without x data
    public void addGraphData(floatBufferRepresentation[] graphY, double min, double max) {
        if (graphY == null || graphY[0] == null)
            return;
        //Create standard x data with indices
        if (graphY[0].size == 0) {
            addGraphData(graphY, min, max, graphY, min, max);
            return;
        }
        FloatBuffer data = ByteBuffer.allocateDirect(graphY[0].size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        graphY[0].data.position(0);
        for (int i = 0; i < graphY[0].size; i++)
            data.put((float)i);

        floatBufferRepresentation[] graphX = new floatBufferRepresentation[graphY.length];
        for (int i = 0; i < graphY.length; i++)
            graphX[i] = null;
        graphX[0] = new floatBufferRepresentation(data, 0, graphY[0].size);

        //Call the full addGraphData with the artificial x data
        addGraphData(graphY, min, max, graphX, 0, graphY[0].size-1);
    }

    //Interface to set axis labels
    public void setLabel(String labelX, String labelY) {
        this.labelX = labelX;
        this.labelY = labelY;
    }

    //Interface to configure logarithmic scales
    public void setLogScale(boolean logX, boolean logY) {
        this.logX = logX;
        this.logY = logY;
        graphSetup.logX = logX;
        graphSetup.logY = logY;
    }

    public void setPrecision(int xPrecision, int yPrecision) {
        this.xPrecision = xPrecision;
        this.yPrecision = yPrecision;
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
                tics = new double[digitRange/magStep];
                for (int i = 0; i < digitRange / magStep; i++) { //Fill the array with powers of ten
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

    @Override
    //Draw the graph!
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Resources res = getResources();

        paint.setTextSize(res.getDimension(R.dimen.graph_font));

        double workingMinX = Double.isNaN(zoomMinX) ? minX : zoomMinX;
        double workingMaxX = Double.isNaN(zoomMaxX) ? maxX : zoomMaxX;
        double workingMinY = Double.isNaN(zoomMinY) ? minY : zoomMinY;
        double workingMaxY = Double.isNaN(zoomMaxY) ? maxY : zoomMaxY;

        //Stretch x slightly to give a little headroom... Also force a range if it is zero
        if (!logX) {
            double extraX = (workingMaxX - workingMinX) * 0.05;
            if (extraX == 0)
                extraX = workingMaxX * 0.05;
            workingMaxX += extraX;
            workingMinX -= extraX;
        }

        //Stretch y slightly to give a little headroom... Also force a range if it is zero
        if (!logY) {
            double extraY = (workingMaxY - workingMinY) * 0.05;
            if (extraY == 0)
                extraY = workingMaxY * 0.05;
            workingMaxY += extraY;
            workingMinY -= extraY;
        }

        //On log scales zero is a problem. We just set a minimum which works for most plots.
        //However, this is a compromise.
        if (logX && workingMinX < 0.001)
            workingMinX = 0.001;
        if (logY && workingMinY < 0.001)
            workingMinY = 0.001;

        //Generate the tics
        double[] xTics = getTics(workingMinX, workingMaxX, maxXTics, logX);
        double[] yTics = getTics(workingMinY, workingMaxY, maxYTics, logY);

        //Calculate area...
        int w = this.getWidth();
        int h = this.getHeight();

        //Consider room for the labels and the tics
        int graphB = (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.2);
        if (labelX != null)
            graphB += (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.2);
        int graphL = 0;
        for (double tic : yTics) {
            double tw = paint.measureText(String.format("%."+yPrecision+"g", tic))+res.getDimension(R.dimen.graph_font)/2.;
            if (tw > graphL)
                graphL = (int)Math.ceil(tw);
        }
        if (labelY != null)
            graphL += (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.2);

        int graphW = w-graphL;
        int graphH = h-graphB;

        //Labels for the tics
        paint.setColor(res.getColor(R.color.mainExp));
        paint.setStrokeWidth(1);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.CENTER);
        for (double tic : xTics) {
            if (tic < workingMinX || tic > workingMaxX)
                continue;
            double x;
            if (logX)
                x = (Math.log(tic/workingMinX))/(Math.log(workingMaxX/workingMinX))*(graphW-1)+graphL;
            else
                x = (tic-workingMinX)/(workingMaxX-workingMinX)*(graphW-1)+graphL;
            canvas.drawText(String.format("%."+xPrecision+"g", tic), (float)x, h-graphB+(float)(res.getDimensionPixelSize(R.dimen.graph_font)*1.1), paint);
        }
        paint.setTextAlign(Paint.Align.RIGHT);
        for (double tic : yTics) {
            if (tic < workingMinY || tic > workingMaxY)
                continue;
            double y;
            if (logY)
                y = h-(Math.log(tic/workingMinY))/(Math.log(workingMaxY/workingMinY))*(graphH-1)-graphB;
            else
                y = h-(tic-workingMinY)/(workingMaxY-workingMinY)*(graphH-1)-graphB;
            canvas.drawText(String.format("%."+yPrecision+"g", tic), graphL-(float)(res.getDimensionPixelSize(R.dimen.graph_font)*0.2), (float)(y+(res.getDimensionPixelSize(R.dimen.graph_font)*0.4)), paint);
        }

        //Labels
        paint.setTextAlign(Paint.Align.CENTER);
        if (labelX != null)
            canvas.drawText(labelX, graphL+graphW/2, h-(int)(res.getDimensionPixelSize(R.dimen.graph_font)*0.1), paint);
        if (labelY != null) {
            canvas.save();
            canvas.rotate(-90, res.getDimensionPixelSize(R.dimen.graph_font), graphH / 2);
            canvas.drawText(labelY, res.getDimensionPixelSize(R.dimen.graph_font), graphH / 2, paint);
            canvas.restore();
        }

        //Draw rect around graph
        paint.setColor(res.getColor(R.color.mainExp));
        paint.setStrokeWidth(3);
        paint.setAlpha(255);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(graphL + 1, 1, w - 1, h - graphB - 1, paint);

        //Draw graph in OpenGL
        graphSetup.setPlotBounds(graphL, 0, graphW, graphH);
        graphSetup.setTics(xTics, yTics, plotRenderer);
        graphSetup.setDataBounds((float)workingMinX, (float)workingMaxX, (float)workingMinY, (float)workingMaxY);
        plotRenderer.requestRender();

    }
}

