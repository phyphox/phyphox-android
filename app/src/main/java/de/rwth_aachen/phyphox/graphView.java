package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Region;
import android.util.Log;
import android.view.View;

//The graphView class implements an Android view which displays a data graph

public class graphView extends View {
    private Double[][] graphX; //The x data to be displayed
    private Double[][] graphY; //The y data to be displayed

    private boolean forceFullDataset = false; //If true, all data points are shown. If false, some datapoints will be skipped if much more data is availbale than required by the resolution

    private int historyLength; //If set to n > 1 the graph will also show the last n sets in a different color
    private int historyFilled; //Tracks the number of entries in the history

    private double rangeMinX, rangeMaxX, rangeMinY, rangeMaxY; //User defined fixed ranges. Min and max for x- and y-axis
    private double minX, maxX, minY, maxY; //If a rescale is triggered, the global min and max for x and y values is stored here

    boolean line = false; //Show lines instead of points?

    private final static int maxXTics = 6; //Constant to set a target number of tics on the x axis
    private final static int maxYTics = 6; //Constant to set a target number of tics on the y axis
    private String labelX = null; //Label for the x-axis
    private String labelY = null; //Label for the y-axis
    private boolean logX = false; //logarithmic scale for the x-axis?
    private boolean logY = false; //logarithmic scale for the y-axis?

    Paint paint; //Anti-Aliased paint used all over this class
    private Resources res; //Reference to resources (for strings and colors)

    //Simple constructor just needs a context to call the View constructor
    //Initialize some stuff...
    public graphView(Context context) {
        super(context);
        res = getResources();
        setHistoryLength(1);
        setRange(Double.NaN, Double.NaN, Double.NaN, Double.NaN); //No fixed range
        rescale(); //Calculate initial scale. At this point it will just set all min and max to +inf and -inf
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    //Interface to switch between lines and points
    public void setLine(boolean line) {
        this.line = line;
    }

    //Interface to set the history length
    public void setHistoryLength(int length) {
        this.historyLength = length;
        this.historyFilled = 0;
        graphX = new Double[historyLength][];
        graphY = new Double[historyLength][];
    }

    //Interface to force the display the full data set vs. skipping datapoints if there are more than neccessary at the current resolution
    public void setForceFullDataset(boolean forceFullDataset) {
        this.forceFullDataset = forceFullDataset;
    }

    //Rescale any non-fixed ranges (those set to NaN)
    public void rescale() {
        minX = Double.POSITIVE_INFINITY;
        maxX = Double.NEGATIVE_INFINITY;
        minY = Double.POSITIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < historyFilled; i++) { //Consider every history entry
            for (int j = 0; graphX[i] != null && j < graphX[i].length; j++) { //Consider all the x-data
                if (Double.isInfinite(graphX[i][j]) || Double.isNaN(graphX[i][j])) //Finite data only
                    continue;
                if (graphX[i][j] < minX)
                    minX = graphX[i][j];
                if (graphX[i][j] > maxX)
                    maxX = graphX[i][j];
            }
            for (int j = 0; graphY[i] != null && j < graphY[i].length; j++) { //Consider all the y-data
                if (Double.isInfinite(graphY[i][j]) || Double.isNaN(graphY[i][j])) //Finite data only
                    continue;
                if (graphY[i][j] < minY)
                    minY = graphY[i][j];
                if (graphY[i][j] > maxY)
                    maxY = graphY[i][j];
            }
        }
    }

    //Add a new graph and (if enabled) push the old graphs back into history
    public void addGraphData(Double[] graphY, Double[] graphX) {
        //Push back data in the history
        for (int i = historyFilled-1; i > 0; i--) {
            this.graphY[i] = this.graphY[i-1];
            this.graphX[i] = this.graphX[i-1];
        }

        //set new graph
        this.graphY[0] = graphY;
        this.graphX[0] = graphX;

        //History increases
        historyFilled++;
        if (historyFilled > historyLength) //History full? Limit it.
            historyFilled = historyLength;

        //Rescale and invalidate to update everything
        this.rescale();
        this.invalidate();
    }

    //This overloads addGraphData to take pure y-data without x data
    public void addGraphData(Double[] graphY) {
        //Create standard x data with indices
        Double [] graphX = new Double[graphY.length];
        for (int i = 0; i < graphY.length; i++)
            if (!Double.isNaN(graphY[i]))
                graphX[i] = (double)i;

        //Call the full addGraphData with the artificial x data
        addGraphData(graphY, graphX);
    }

    //Interface to set fixed ranges
    public void setRange(double minX, double maxX, double minY, double maxY) {
        rangeMinX = minX;
        rangeMaxX = maxX;
        rangeMinY = minY;
        rangeMaxY = maxY;
        this.invalidate(); //Invalidate to redraw
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
                tics = new double[digitRange/magStep];
                for (int i = 0; i < digitRange / magStep; i++) { //Fill the array with powers of ten
                    tics[i] = first;
                    first *= 10;
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

        paint.setTextSize(res.getDimension(R.dimen.graph_font));

        //Set limits - range[Min|Max][X|Y] is the user set one - if it is not set (NaN) use [min|max][X|Y] from rescaling
        double effectiveMinX, effectiveMaxX, effectiveMinY, effectiveMaxY;
        if (Double.isNaN(rangeMinX))
            effectiveMinX = minX;
        else
            effectiveMinX = rangeMinX;
        if (Double.isNaN(rangeMaxX))
            effectiveMaxX = maxX;
        else
            effectiveMaxX = rangeMaxX;
        if (Double.isNaN(rangeMinY))
            effectiveMinY = minY;
        else
            effectiveMinY = rangeMinY;
        if (Double.isNaN(rangeMaxY))
            effectiveMaxY = maxY;
        else
            effectiveMaxY = rangeMaxY;

        //Stretch y slightly to give a little headroom... Also force a range if it is zero
        if (!logY) {
            double extraY = (effectiveMaxY - effectiveMinY) * 0.05;
            if (extraY == 0)
                extraY = effectiveMaxY * 0.05;
            effectiveMaxY += extraY;
            effectiveMinY -= extraY;
        }

        //On log scales zero is a problem. We just set a minimum which works for most plots.
        //However, this is a compromise.
        if (logX && effectiveMinX < 0.001)
            effectiveMinX = 0.001;
        if (logY && effectiveMinY < 0.001)
            effectiveMinY = 0.001;

        //Generate the tics
        double[] xTics = getTics(effectiveMinX, effectiveMaxX, maxXTics, logX);
        double[] yTics = getTics(effectiveMinY, effectiveMaxY, maxYTics, logY);

        //Calculate area...
        int w = this.getWidth();
        int h = this.getHeight();

        //Consider room for the labels and the tics
        int graphB = (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.2);
        if (labelX != null)
            graphB += (int)(res.getDimensionPixelSize(R.dimen.graph_font)*1.2);
        int graphL = 0;
        for (double tic : yTics) {
            double tw = paint.measureText(String.format("%.3g", tic))+res.getDimension(R.dimen.graph_font)/2.;
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
            if (tic < effectiveMinX || tic > effectiveMaxX)
                continue;
            double x;
            if (logX)
                x = (Math.log(tic/effectiveMinX))/(Math.log(effectiveMaxX/effectiveMinX))*(graphW-1)+graphL;
            else
                x = (tic-effectiveMinX)/(effectiveMaxX-effectiveMinX)*(graphW-1)+graphL;
            canvas.drawText(String.format("%.3g", tic), (float)x, h-graphB+(float)(res.getDimensionPixelSize(R.dimen.graph_font)*1.1), paint);
        }
        paint.setTextAlign(Paint.Align.RIGHT);
        for (double tic : yTics) {
            if (tic < effectiveMinY || tic > effectiveMaxY)
                continue;
            double y;
            if (logY)
                y = h-(Math.log(tic/effectiveMinY))/(Math.log(effectiveMaxY/effectiveMinY))*(graphH-1)-graphB;
            else
                y = h-(tic-effectiveMinY)/(effectiveMaxY-effectiveMinY)*(graphH-1)-graphB;
            canvas.drawText(String.format("%.3g", tic), graphL-(float)(res.getDimensionPixelSize(R.dimen.graph_font)*0.2), (float)(y+(res.getDimensionPixelSize(R.dimen.graph_font)*0.5)), paint);
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

        canvas.save();
        canvas.clipRect(graphL,0,w,h-graphB, Region.Op.INTERSECT);

        //Draw grid
        paint.setColor(res.getColor(R.color.grid));
        paint.setAlpha(255);
        paint.setStrokeCap(Paint.Cap.SQUARE);

        for (double tic : xTics) {
            double x;
            if (logX)
                x = (Math.log(tic/effectiveMinX))/(Math.log(effectiveMaxX/effectiveMinX))*(graphW-1)+graphL;
            else
                x = (tic-effectiveMinX)/(effectiveMaxX-effectiveMinX)*(graphW-1)+graphL;
            canvas.drawLine((float)x, 0, (float)x, h-graphB, paint);
        }
        for (double tic : yTics) {
            double y;
            if (logY)
                y = h-(Math.log(tic/effectiveMinY))/(Math.log(effectiveMaxY/effectiveMinY))*(graphH-1)-graphB;
            else
                y = h-(tic-effectiveMinY)/(effectiveMaxY-effectiveMinY)*(graphH-1)-graphB;
            canvas.drawLine(graphL, (float)y, w, (float)y, paint);
        }

        //Draw graph
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (effectiveMinX < effectiveMaxX) {
            for (int j = historyFilled-1; j >= 0; j--) {
                if (graphY[j] != null && graphX[j] != null) {
                    if (j == 0) {
                        if (this.line)
                            paint.setStrokeWidth(3);
                        else
                            paint.setStrokeWidth(5);
                        paint.setColor(res.getColor(R.color.highlight));
                        paint.setAlpha(255);
                    } else {
                        if (this.line)
                            paint.setStrokeWidth(2);
                        else
                            paint.setStrokeWidth(3);
                        paint.setColor(res.getColor(R.color.mainExp));
                        paint.setAlpha(150-(j+1)*150/historyLength);
                    }
                    double lastX = Double.NaN;
                    double lastY = Double.NaN;

                    double avgX = Double.NaN; //Average for single x-axis pixel
                    double avgY = Double.NaN; //Average for single y-axis pixel
                    int avgCount = 0; //Keeps track of the number of points that have been added
                                      //to the same x-axis pixel for averaging

                    for (int i = 0; i < graphX[j].length && i < graphY[j].length; i++) {
                        //Calculate x/y in display coordinates
                        double thisX;
                        double thisY;
                        if (logX)
                            thisX = Math.round((Math.log(graphX[j][i]/effectiveMinX))/(Math.log(effectiveMaxX/effectiveMinX))*(graphW-1)+graphL);
                        else
                            thisX = Math.round((graphX[j][i]-effectiveMinX)/(effectiveMaxX-effectiveMinX)*(graphW-1)+graphL);
                        if (logY)
                            thisY = h-(Math.log(graphY[j][i]/effectiveMinY))/(Math.log(effectiveMaxY/effectiveMinY))*(graphH-1)-graphB;
                        else
                            thisY = h-(graphY[j][i]-effectiveMinY)/(effectiveMaxY-effectiveMinY)*(graphH-1)-graphB;

                        if (forceFullDataset) {
                            //We shall draw every single point.
                            avgX = thisX;
                            avgY = thisY;
                        } else {
                            if (Double.isNaN(thisX) || Double.isNaN(thisY))
                                continue;
                            if (thisX == avgX) {
                                //This has to be part of the current average. Add it and continue
                                avgY += thisY;
                                avgCount++;
                                if (i < graphX[j].length-1 && i < graphY[j].length-1)
                                    continue; //There might be another point for this average...
                            }
                            //So we are done with this x pixel (Either because we found a new one or
                            //because this is the last point in the data set.
                            if (avgCount == 0)
                                avgY = Double.NaN;
                            else
                                avgY /= (double)avgCount; //Average
                        }

                        //Now draw a line or a point
                        if (this.line) {
                            if (!(Double.isNaN(lastX) || Double.isNaN(lastY) || Double.isNaN(avgX) || Double.isNaN(avgY))) {
                                canvas.drawLine((float)lastX, (float)lastY, (float)avgX, (float)avgY, paint);
                            }
                        } else {
                            if (!(Double.isNaN(avgX) || Double.isNaN(avgY))) {
                                canvas.drawPoint((float) avgX, (float) avgY, paint);
                            }
                        }

                        //Remember this point for next iteration (to draw a line and to know which
                        // points to average)
                        lastX = avgX;
                        lastY = avgY;

                        //And reset averaging
                        if (!Double.isNaN(thisY)) {
                            avgCount = 1;
                            avgY = thisY;
                        } else {
                            avgCount = 0;
                            avgY = 0.;
                        }
                        avgX = thisX;
                    }
                }
            }

        } else {
            paint.setColor(res.getColor(R.color.mainExp));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(res.getString(R.string.noDrawableData), graphL+graphW/2, (h-graphB+res.getDimension(R.dimen.graph_font))/2, paint);
        }
        canvas.restore();

        //Draw rect around graph
        paint.setColor(res.getColor(R.color.mainExp));
        paint.setStrokeWidth(3);
        paint.setAlpha(255);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(graphL + 1, 1, w - 1, h - graphB - 1, paint);

    }
}

