package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Region;
import android.util.Log;
import android.view.View;

public class graphView extends View {
    private int historyLength;
    private int historyFilled;
    private Double[][] graphX;
    private Double[][] graphY;
    private double rangeMinX, rangeMaxX, rangeMinY, rangeMaxY;
    private double minX, maxX, minY, maxY;
    private Resources res;
    boolean line = false;
    private int maxXTics = 6;
    private int maxYTics = 6;
    private String labelX = null;
    private String labelY = null;
    private boolean logX = false;
    private boolean logY = false;
    Paint paint;

    public graphView(Context context) {
        super(context);
        res = getResources();
        setHistoryLength(1);
        setRange(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        rescale();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setLine(boolean line) {
        this.line = line;
    }

    public void setHistoryLength(int length) {
        this.historyLength = length;
        this.historyFilled = 0;
        graphX = new Double[historyLength][];
        graphY = new Double[historyLength][];
    }

    public void rescale() {
        minX = Double.POSITIVE_INFINITY;
        maxX = Double.NEGATIVE_INFINITY;
        minY = Double.POSITIVE_INFINITY;
        maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < historyFilled; i++) {
            for (int j = 0; j < graphX[i].length; j++) {
                if (Double.isInfinite(graphX[i][j]) || Double.isNaN(graphX[i][j]))
                    continue;
                if (graphX[i][j] < minX)
                    minX = graphX[i][j];
                if (graphX[i][j] > maxX)
                    maxX = graphX[i][j];
            }
            for (int j = 0; j < graphY[i].length; j++) {
                if (Double.isInfinite(graphY[i][j]) || Double.isNaN(graphY[i][j]))
                    continue;
                if (graphY[i][j] < minY)
                    minY = graphY[i][j];
                if (graphY[i][j] > maxY)
                    maxY = graphY[i][j];
            }
        }
    }

    public void addGraphData(Double[] graphY, Double[] graphX) {
        for (int i = historyFilled-1; i > 0; i--) {
            this.graphY[i] = this.graphY[i-1];
            this.graphX[i] = this.graphX[i-1];
        }
        this.graphY[0] = graphY;
        this.graphX[0] = graphX;
        historyFilled++;
        if (historyFilled > historyLength)
            historyFilled = historyLength;
        this.rescale();
        this.invalidate();
    }

    public void addGraphData(Double[] graphY) {
        Double [] graphX = new Double[graphY.length];
        for (int i = 0; i < graphY.length; i++)
            if (!Double.isNaN(graphY[i]))
                graphX[i] = (double)i;
        addGraphData(graphY, graphX);
    }

    public void setRange(double minX, double maxX, double minY, double maxY) {
        rangeMinX = minX;
        rangeMaxX = maxX;
        rangeMinY = minY;
        rangeMaxY = maxY;
        this.invalidate();
    }

    public void setLabel(String labelX, String labelY) {
        this.labelX = labelX;
        this.labelY = labelY;
    }

    public void setLogScale(boolean logX, boolean logY) {
        this.logX = logX;
        this.logY = logY;
    }

    private double[] getTics(double min, double max, int maxTics, boolean log){
        if (!(max > min))
            return new double[0];
        if (Double.isInfinite(min) || Double.isNaN(min) || Double.isInfinite(max) || Double.isNaN(max))
            return new double[0];
        if (log) {
            if (min < 0)
                return new double[0];
            double logMax = Math.log10(max);
            double logMin = Math.log10(min);

            int digitRange = (int)(Math.floor(logMax)-Math.ceil(logMin));

            if (digitRange < 1)
                return new double[0];

            int magStep = 1;
            while (digitRange+1 > maxTics * magStep)
                magStep++;

            double first = Math.pow(10, Math.ceil(logMin));
            double[] tics = new double[(digitRange+1)/magStep];

            for (int i = 0; i < (digitRange+1)/magStep; i++) {
                tics[i] = first;
                first *= 10;
            }

            return tics;
        }

        double range = max-min;
        double stepFactor = Math.pow(10,Math.floor(Math.log10(range))-1);
        double step = 1.;
        double steps = range/stepFactor;
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

        int iSteps = (int)Math.ceil(range/step);

        double first = Math.ceil(min/step)*step;
        double[] tics = new double[iSteps];

        for (int i = 0; i < iSteps; i++) {
            tics[i] = first + i * step;
        }

        return tics;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paint.setTextSize(res.getDimension(R.dimen.graph_font));

        //Set limits
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

        if (logX && effectiveMinX < 0.001)
            effectiveMinX = 0.001;
        if (logY && effectiveMinY < 0.001)
            effectiveMinY = 0.001;

        double[] xTics = getTics(effectiveMinX, effectiveMaxX, maxXTics, logX);
        double[] yTics = getTics(effectiveMinY, effectiveMaxY, maxYTics, logY);

        //Calculate area...
        int w = this.getWidth();
        int h = this.getHeight();

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

        //Label tics
        paint.setColor(res.getColor(R.color.main));
        paint.setStrokeWidth(1);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.CENTER);
        for (double tic : xTics) {
            if (tic < effectiveMinX || tic > effectiveMaxX)
                continue;
            double x;
            if (logX)
                x = (Math.log(tic/effectiveMinX))/(Math.log(effectiveMaxX/effectiveMinX))*graphW+graphL;
            else
                x = (tic-effectiveMinX)/(effectiveMaxX-effectiveMinX)*graphW+graphL;
            canvas.drawText(String.format("%.3g", tic), (float)x, h-graphB+(float)(res.getDimensionPixelSize(R.dimen.graph_font)*1.1), paint);
        }
        paint.setTextAlign(Paint.Align.RIGHT);
        for (double tic : yTics) {
            if (tic < effectiveMinY || tic > effectiveMaxY)
                continue;
            double y;
            if (logY)
                y = h-(Math.log(tic/effectiveMinY))/(Math.log(effectiveMaxY/effectiveMinY))*graphH-graphB;
            else
                y = h-(tic-effectiveMinY)/(effectiveMaxY-effectiveMinY)*graphH-graphB;
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
                x = (Math.log(tic/effectiveMinX))/(Math.log(effectiveMaxX/effectiveMinX))*graphW+graphL;
            else
                x = (tic-effectiveMinX)/(effectiveMaxX-effectiveMinX)*graphW+graphL;
            canvas.drawLine((float)x, 0, (float)x, h-graphB, paint);
        }
        for (double tic : yTics) {
            double y;
            if (logY)
                y = h-(Math.log(tic/effectiveMinY))/(Math.log(effectiveMaxY/effectiveMinY))*graphH-graphB;
            else
                y = h-(tic-effectiveMinY)/(effectiveMaxY-effectiveMinY)*graphH-graphB;
            canvas.drawLine(graphL, (float)y, w, (float)y, paint);
        }

        //Draw graph
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (effectiveMinX < effectiveMaxX) {
            for (int j = historyFilled-1; j >= 0; j--) {
                if (graphY[j] != null && graphX[j] != null) {
                    if (j == 0) {
                        paint.setStrokeWidth(3);
                        paint.setColor(res.getColor(R.color.highlight));
                        paint.setAlpha(255);
                    } else {
                        paint.setStrokeWidth(2);
                        paint.setColor(res.getColor(R.color.main));
                        paint.setAlpha(255-(j+1)*255/historyLength);
                    }
                    double lastX = Double.NaN;
                    double lastY = Double.NaN;
                    for (int i = 0; i < graphX[j].length && i < graphY[j].length; i++) {
                        double thisX;
                        double thisY;
                        if (logX)
                            thisX = (Math.log(graphX[j][i]/effectiveMinX))/(Math.log(effectiveMaxX/effectiveMinX))*graphW+graphL;
                        else
                            thisX = (graphX[j][i]-effectiveMinX)/(effectiveMaxX-effectiveMinX)*graphW+graphL;
                        if (logY)
                            thisY = h-(Math.log(graphY[j][i]/effectiveMinY))/(Math.log(effectiveMaxY/effectiveMinY))*graphH-graphB;
                        else
                            thisY = h-(graphY[j][i]-effectiveMinY)/(effectiveMaxY-effectiveMinY)*graphH-graphB;
                        if (this.line) {
                            if (!(Double.isNaN(lastX) || Double.isNaN(lastY))) {
                                canvas.drawLine((float)lastX, (float)lastY, (float)thisX, (float)thisY, paint);
                            }
                            lastX = thisX;
                            lastY = thisY;
                        } else {
                            canvas.drawPoint((float)thisX, (float)thisY, paint);
                        }
                    }
                }
            }

        } else {
            paint.setColor(res.getColor(R.color.main));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No drawable data.", graphL+graphW/2, (h-graphB+res.getDimension(R.dimen.graph_font))/2, paint);
        }
        canvas.restore();

        //Draw rect around graph
        paint.setColor(res.getColor(R.color.main));
        paint.setStrokeWidth(3);
        paint.setAlpha(255);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(graphL+1, 1, w-1, h - graphB-1, paint);

    }
}

