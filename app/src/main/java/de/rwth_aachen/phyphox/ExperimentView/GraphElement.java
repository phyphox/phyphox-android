package de.rwth_aachen.phyphox.ExperimentView;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.DataBuffer;
import de.rwth_aachen.phyphox.DataExport;
import de.rwth_aachen.phyphox.DataOutput;
import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.ExperimentTimeReference;
import de.rwth_aachen.phyphox.ExperimentTimeReferenceSet;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.GraphView;
import de.rwth_aachen.phyphox.ExperimentView.GraphView.InteractiveGraphView;
import de.rwth_aachen.phyphox.FloatBufferRepresentation;
import de.rwth_aachen.phyphox.PhyphoxExperiment;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.helper.RGB;

//GraphElement implements a graph that displays y vs. x arrays from the dataBuffer
//This class mostly wraps the graphView, which (being rather complex) is implemented in its own
//class. See GraphView.java...
public class GraphElement extends ExpViewElement implements Serializable {
    private final GraphElement self;
    transient private GraphView gv = null;
    transient private InteractiveGraphView interactiveGV = null;
    private double aspectRatio; //The aspect ratio defines the height of the graph view based on its width (aspectRatio=width/height)
    transient private FloatBufferRepresentation[] dataX; //The x data to be displayed
    transient private FloatBufferRepresentation[] dataY; //The y data to be displayed
    transient private List<ExperimentTimeReferenceSet>[] timeReferencesX;
    transient private List<ExperimentTimeReferenceSet>[] timeReferencesY;
    private double dataMinX, dataMaxX, dataMinY, dataMaxY, dataMinZ, dataMaxZ;

    private boolean isExclusive = false;
    private int margin;

    private Vector<GraphView.Style> style = new Vector<>(); //Show lines instead of points?
    private Vector<Integer> mapWidth = new Vector<>();
    private Vector<Integer> colorScale = new Vector<>();
    private boolean showColorScale;
    private boolean interpolateMapColors = true;
    private int historyLength = 1; //If set to n > 1 the graph will also show the last n sets in a different color
    private int nCurves = 1;
    private String labelX = null; //Label for the x-axis
    private String labelY = null; //Label for the y-axis
    private String labelZ = null; //Label for the z-axis
    private String unitX = null; //Label for the x-axis
    private String unitY = null; //Label for the y-axis
    private String unitZ = null; //Label for the z-axis
    private String unitYX = null; //Unit for slope (i.e. y/x)
    private boolean partialUpdate = false; //Allow partialUpdate of newly added data points instead of transfering the whole dataset each time (web-interface)
    private boolean timeOnX = false; //x-axis is time axis?
    private boolean timeOnY = false; //y-axis is time axis?
    private boolean absoluteTime = false; //Use system time as default?
    private boolean linearTime = false; //time data is not given in experiment time (which pauses with the experiment) but as seconds since 1970 (ignoring pauses)
    private boolean hideTimeMarkers = false; //Do not show the red markers that indicate times while the phyphox experiment was not running.
    private boolean logX = false; //logarithmic scale for the x-axis?
    private boolean logY = false; //logarithmic scale for the y-axis?
    private boolean logZ = false; //logarithmic scale for the z-axis?
    private boolean suppressScientificNotation = false;
    private int xPrecision = -1;
    private int yPrecision = -1;
    private int zPrecision = -1;
    private Vector<Double> lineWidth = new Vector<>();
    private Vector<RGB> color = new Vector<>();

    private String gridColor;

    GraphView.ScaleMode scaleMinX = GraphView.ScaleMode.auto;
    GraphView.ScaleMode scaleMaxX = GraphView.ScaleMode.auto;
    GraphView.ScaleMode scaleMinY = GraphView.ScaleMode.auto;
    GraphView.ScaleMode scaleMaxY = GraphView.ScaleMode.auto;
    GraphView.ScaleMode scaleMinZ = GraphView.ScaleMode.auto;
    GraphView.ScaleMode scaleMaxZ = GraphView.ScaleMode.auto;

    double minX = 0.;
    double maxX = 0.;
    double minY = 0.;
    double maxY = 0.;
    double minZ = 0.;
    double maxZ = 0.;

    boolean followX = false;

    GraphView.ZoomState zoomState = null;

    final String warningText;

    String pickLabel = null;
    private Vector<DataOutput> outputs = null;
    private Double[] newPickData = null;
    private Double[] currentPickData = null;
    private boolean pickDataChangedExternally = false;

    //Quite usual constructor...
    public GraphElement(String label, String visibility, Vector<String> valueOutputs, Vector<String> inputs, Resources res) {
        super(label, visibility, valueOutputs, inputs, res);
        this.self = this;

        margin = res.getDimensionPixelSize(R.dimen.activity_vertical_margin);

        aspectRatio = 2.5;
        gridColor = String.format("%08x", res.getColor(R.color.phyphox_white_50_black_50)).substring(2);
        nCurves = (inputs.size()+1)/2;

        for (int i = 0; i < nCurves; i++) {
            color.add(new RGB(res.getColor(R.color.phyphox_primary)));
            lineWidth.add(1.0);
            style.add(GraphView.Style.lines);
            mapWidth.add(0);
            dataX = new FloatBufferRepresentation[nCurves];
            dataY = new FloatBufferRepresentation[nCurves];
            timeReferencesX =  new ArrayList[nCurves];
            timeReferencesY =  new ArrayList[nCurves];
        }

        warningText = res.getString(R.string.remoteColorMapWarning).replace("'", "\\'");
    }

    public void setPickConfig(String pickLabel, Vector<DataOutput> outputs) {
        this.pickLabel = pickLabel;
        this.outputs = outputs;
    }

    //Interface to change the height of the graph
    public void setAspectRatio(double aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public void setLineWidth(double lineWidth, int i) {
        this.lineWidth.set(i, lineWidth);
        if (gv != null)
            gv.setLineWidth(lineWidth, i);
    }

    public void setLineWidth(double lineWidth) {
        for (int i = 0; i < nCurves || i < historyLength; i++)
            setLineWidth(lineWidth, i);
    }

    public void setColor(RGB color, int i, Resources res) {
        this.color.set(i, color);
        if (gv != null)
            gv.setColor(color.autoLightColor(res).intColor(), i);
    }

    public void setColor(RGB color, Resources res) {
        for (int i = 0; i < nCurves || i < historyLength; i++) {
            setColor(color, i, res);
        }
    }

    public void setStyle(GraphView.Style style, int i) {
        this.style.set(i, style);
        if (gv != null)
            gv.setStyle(style, i);
    }

    //Interface to switch between points and lines
    public void setStyle(GraphView.Style style) {
        for (int i = 0; i < nCurves || i < historyLength; i++)
            setStyle(style, i);
    }

    public void setColorScale(Vector<Integer> scale) {
        this.colorScale = scale;
        if (gv != null)
            gv.setColorScale(scale);
    }

    public void setMapWidth(int width, int i) {
        this.mapWidth.set(i, width);
        if (gv != null)
            gv.setMapWidth(width, i);
    }

    public void setMapWidth(int width) {
        for (int i = 0; i < nCurves || i < historyLength; i++)
            setMapWidth(width, i);
    }

    public  void setShowColorScale(boolean showColorScale){
        this.showColorScale = showColorScale;
    }

    public void setInterpolateMapColors(boolean interpolate) {
        this.interpolateMapColors = interpolate;
        if (gv != null)
            gv.setInterpolateMapColors(interpolate);
    }

    public void setScaleModeX(GraphView.ScaleMode minMode, double minV, GraphView.ScaleMode maxMode, double maxV) {
        this.scaleMinX = minMode;
        this.scaleMaxX = maxMode;
        this.minX = minV;
        this.maxX = maxV;
        if (gv != null)
            gv.setScaleModeX(minMode, minV, maxMode, maxV);
    }

    public void setScaleModeY(GraphView.ScaleMode minMode, double minV, GraphView.ScaleMode maxMode, double maxV) {
        this.scaleMinY = minMode;
        this.scaleMaxY = maxMode;
        this.minY = minV;
        this.maxY = maxV;
        if (gv != null)
            gv.setScaleModeY(minMode, minV, maxMode, maxV);
    }

    public void setScaleModeZ(GraphView.ScaleMode minMode, double minV, GraphView.ScaleMode maxMode, double maxV) {
        this.scaleMinZ = minMode;
        this.scaleMaxZ = maxMode;
        this.minZ = minV;
        this.maxZ = maxV;
        if (gv != null)
            gv.setScaleModeZ(minMode, minV, maxMode, maxV);
    }

    public void setFollowX(boolean followX) {
        this.followX = followX;
        if (followX) {
            this.scaleMinX = GraphView.ScaleMode.fixed;
            this.scaleMaxX = GraphView.ScaleMode.fixed;
            this.partialUpdate = true;
        }
    }

    //Interface to set a history length
    public void setHistoryLength(int hl) {
        this.historyLength = hl;
        if (gv != null)
            gv.setHistoryLength(hl);
        if (hl > 1) {
            dataX = new FloatBufferRepresentation[1];
            dataY = new FloatBufferRepresentation[1];
        }
    }

    //Interface to set the axis labels.
    public void setLabel(String labelX, String labelY, String labelZ, String unitX, String unitY, String unitZ, String unitYX) {
        this.labelX = labelX;
        this.labelY = labelY;
        this.labelZ = labelZ;
        this.unitX = unitX;
        this.unitY = unitY;
        this.unitZ = unitZ;
        this.unitYX = unitYX;
        if (gv != null)
            gv.setLabel(labelX, labelY, labelZ, unitX, unitY, unitZ, unitYX);
    }

    public void setTimeAxes(boolean timeOnX, boolean timeOnY, boolean absoluteTime, boolean linearTime, boolean hideTimeMarkers) {
        this.timeOnX = timeOnX;
        this.timeOnY = timeOnY;
        this.absoluteTime = absoluteTime;
        this.linearTime = linearTime;
        this.hideTimeMarkers = hideTimeMarkers;
    }

    public void setSuppressScientificNotation(boolean suppressScientificNotation) {
        this.suppressScientificNotation = suppressScientificNotation;
    }

    //Interface to set log scales
    public void setLogScale(boolean logX, boolean logY, boolean logZ) {
        this.logX = logX;
        this.logY = logY;
        this.logZ = logZ;
    }

    public void setPrecision(int xPrecision, int yPrecision, int zPrecision) {
        this.xPrecision = xPrecision;
        this.yPrecision = yPrecision;
        this.zPrecision = zPrecision;
    }

    //Interface to set partial updates vs. full updates of the data sets
    public void setPartialUpdate(boolean pu) {
        this.partialUpdate = pu;
        if (gv != null)
            gv.graphSetup.incrementalX = pu;
    }

    @Override
    //The update mode is "partial" or "full" as this element uses arrays. The experiment may
    //decide if partial updates are sufficient
    public String getUpdateMode() {
        if (partialUpdate) {
            if (style.get(0) == GraphView.Style.mapXY)
                return "partialXYZ";
            else
                return "partial";
        } else
            return "full";
    }

    @Override
    //Create the actual view in Android
    public void createView(LinearLayout ll, Context c, Resources res, final ExpViewFragment parent, PhyphoxExperiment experiment){
        super.createView(ll, c, res, parent, experiment);

        Context ctx = c;
        Activity act = null;
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                act = (Activity) ctx;
            }
            ctx = ((ContextWrapper)ctx).getBaseContext();
        }

        //Create the graphView
        interactiveGV = new InteractiveGraphView(c);
        interactiveGV.setPickConfig(pickLabel, outputs, data -> newPickData = data);
        if (currentPickData != null)
            interactiveGV.updatePickData(currentPickData);
        gv = interactiveGV.graphView;
        if (zoomState != null)
            gv.zoomState = zoomState;
        else
            zoomState = gv.zoomState;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        interactiveGV.setLayoutParams(lp);
        interactiveGV.setLabel(this.label);
        interactiveGV.setShowColorScale(showColorScale);

        if (act instanceof Experiment) {
            DataExport dataExport = new DataExport(experiment);

            DataExport.ExportSet set = dataExport.new ExportSet(this.label);
            for (int i = 0; i < inputs.size(); i+=2) {
                if (i+1 < inputs.size() && inputs.get(i+1) != null)
                    set.addSource(this.labelX + (i > 1 ? " " + (i / 2 + 1) : "") + (unitX != null && !unitX.isEmpty() ? " (" + unitX +")" : ""), inputs.get(i+1));

                if (style.get(i/2) == GraphView.Style.mapZ)
                    set.addSource((this.labelZ != null ? this.labelZ : "z") + (unitZ != null && !unitZ.isEmpty() ? " (" + unitZ + ")" : ""), inputs.get(i));
                else
                    set.addSource(this.labelY + (i > 1 ? " " + (i / 2 + 1) : "") + (unitY != null && !unitY.isEmpty() ? " (" + unitY + ")" : ""), inputs.get(i));
            }
            dataExport.addSet(set);

            interactiveGV.assignDataExporter(dataExport);
        }

        setTimeReferences(experiment.experimentTimeReference);

        //Send our parameters to the graphView isntance
        if (historyLength > 1)
            gv.setHistoryLength(historyLength);
        else
            gv.setCurves(nCurves);

        for (int i = 0; i < nCurves; i++) {
            gv.setStyle(style.get(i), i);
            gv.setMapWidth(mapWidth.get(i), i);
            gv.setLineWidth(lineWidth.get(i), i);
            gv.setColor(color.get(i).autoLightColor(res).intColor(), i);
        }
        gv.graphSetup.incrementalX = partialUpdate;
        gv.setAspectRatio(aspectRatio);
        gv.setColorScale(colorScale);
        gv.setInterpolateMapColors(interpolateMapColors);
        gv.setScaleModeX(scaleMinX, minX, scaleMaxX, maxX);
        gv.setScaleModeY(scaleMinY, minY, scaleMaxY, maxY);
        gv.setScaleModeZ(scaleMinZ, minZ, scaleMaxZ, maxZ);
        gv.setFollowX(followX);
        gv.setLabel(labelX, labelY, labelZ, unitX, unitY, unitZ, unitYX);
        gv.setTimeAxes(timeOnX, timeOnY);
        gv.setSuppressScientificNotation(suppressScientificNotation);
        gv.setAbsoluteTime(absoluteTime);
        gv.setLinearTime(linearTime);
        gv.setHideTimeMarkers(hideTimeMarkers);
        gv.setLogScale(logX, logY, logZ);
        interactiveGV.allowLogX = logX;
        interactiveGV.allowLogY = logY;
        gv.setPrecision(xPrecision, yPrecision, zPrecision);

        interactiveGV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (self.parent != null) {
                    if (isExclusive) {
                        self.requestLeaveExclusive();
                    } else {
                        interactiveGV.requestFocus();
                        self.parent.requestExclusive(self);
                    }
                }
            }
        });

        //Add the wrapper layout to the linear layout given to this function
        rootView = interactiveGV;
        rootView.setFocusableInTouchMode(false);
        ll.addView(rootView);

    }

    @Override
    //A temporary zoom needs the user's decision, so show the same dialog as tapping the maximized graph
    public void requestLeaveExclusive() {
        if (parent == null)
            return;
        if (interactiveGV != null)
            interactiveGV.leaveDialog(parent, inputs.size() > 1 ? inputs.get(1) : null, inputs.size() > 0 ? inputs.get(0) : null, unitX, unitY);
        else
            parent.leaveExclusive();
    }

    @Override
    public void onFragmentStop(PhyphoxExperiment experiment) {
        super.onFragmentStop(experiment);

        if (interactiveGV != null)
            interactiveGV.stop();
        gv = null;
        interactiveGV = null;
    }

    @Override
    //Create the HTML markup. We use the flot library to plot in JavaScript, so there is not
    //as much to do here as one might expect
    //<div>
    //<span>Label</span>
    //<div>graph</div>
    //</div>
    protected String createViewHTML(){
        return "<div style=\"font-size:"+this.labelSize/.4+"%;\" class=\"graphElement\" id=\"element"+htmlID+"\">" +
                "<span class=\"label\" onclick=\"toggleExclusive("+htmlID+");\">"+this.label+"</span>" +
                (this.style.get(0) == GraphView.Style.mapXY ? "<div class=\"warningIcon\" onclick=\"alert('"+warningText+"')\"></div>" : "")+
                "<div class=\"graphBox\"><div class=\"graphRatio\" style=\"padding-top: "+100.0/this.aspectRatio+"%\"></div><div class=\"graph\"><canvas></canvas></div></div>" +
                "</div>";
    }

    @Override
    public boolean onMayWriteToBuffers(PhyphoxExperiment experiment) {
        if (newPickData == null || outputs == null)
            return false;
        if (outputs.size() != newPickData.length)
            return false;
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i) == null || outputs.get(i).buffer == null)
                continue;
            outputs.get(i).clear(false);
            if (newPickData[i] != null)
                outputs.get(i).append(newPickData[i]);
        }
        newPickData = null;
        return true;
    }

    @Override
    public void onMayReadFromBuffers(PhyphoxExperiment experiment) {
        super.onMayReadFromBuffers(experiment);
        if (!needsUpdate)
            return;
        needsUpdate = false;
        for (int i = 0; i < inputs.size(); i+=2) {
            if (inputs.size() > i+1) {
                DataBuffer x = experiment.getBuffer(inputs.get(i+1));
                if (x != null) {
                    if (timeOnX)
                        timeReferencesX[i/2] = x.getExperimentTimeReferenceSets(linearTime);
                    if (style.get(i/2) == GraphView.Style.hbars)
                        dataX[i/2] = x.getFloatBufferBarValue();
                    else if (style.get(i/2) == GraphView.Style.vbars)
                        dataX[i/2] = x.getFloatBufferBarAxis(lineWidth.get(i/2));
                    else
                        dataX[i/2] = x.getFloatBuffer();
                    if (style.get(i/2) != GraphView.Style.mapZ) {
                        if (i == 0) {
                            dataMinX = x.getMin();
                            dataMaxX = x.getMax();
                        } else {
                            double newMinX = x.getMin();
                            double newMaxX = x.getMax();
                            if (Double.isFinite(newMinX))
                                dataMinX = Double.isFinite(dataMinX) ? Math.min(dataMinX, newMinX) : newMinX;
                            if (Double.isFinite(newMaxX))
                                dataMaxX = Double.isFinite(dataMaxX) ? Math.max(dataMaxX, newMaxX) : newMaxX;
                        }
                    }
                } else {
                    dataX[i/2] = null;
                }
            }

            DataBuffer y = experiment.getBuffer(inputs.get(i));
            if (y != null) {
                if (timeOnY)
                    timeReferencesY[i/2] = y.getExperimentTimeReferenceSets(linearTime);
                if (style.get(i/2) == GraphView.Style.hbars)
                    dataY[i/2] = y.getFloatBufferBarAxis(lineWidth.get(i/2));
                else if (style.get(i/2) == GraphView.Style.vbars)
                    dataY[i/2] = y.getFloatBufferBarValue();
                else
                    dataY[i/2] = y.getFloatBuffer();
                if (style.get(i/2) != GraphView.Style.mapZ) {
                    if (i == 0) {
                        dataMinY = y.getMin();
                        dataMaxY = y.getMax();
                    } else {
                        double newMinY = y.getMin();
                        double newMaxY = y.getMax();
                        if (Double.isFinite(newMinY))
                            dataMinY = Double.isFinite(dataMinY) ? Math.min(dataMinY, newMinY) : newMinY;
                        if (Double.isFinite(newMaxY))
                            dataMaxY = Double.isFinite(dataMaxY) ? Math.max(dataMaxY, newMaxY) : newMaxY;
                    }
                } else {
                    dataMinZ = y.getMin();
                    dataMaxZ = y.getMax();
                }
            } else {
                dataY[i/2] = null;
            }
        }
        if (outputs != null) {
            if (currentPickData == null) {
                currentPickData = new Double[outputs.size()];
            }
            for (int i = 0; i < outputs.size(); i++) {
                if (outputs.get(i) != null && outputs.get(i).buffer != null) {
                    Double value = outputs.get(i).buffer.value;
                    if (!value.equals(currentPickData[i])) {
                        currentPickData[i] = value;
                        pickDataChangedExternally = true;
                    }
                }
            }
        }
    }

    private void setTimeReferences(ExperimentTimeReference experimentTimeReference) {
        if (gv != null) {
            List<Double> starts = new ArrayList<>();
            List<Double> stops = new ArrayList<>();
            double trStop = Double.NaN;
            long trStopSystemTime = 0;
            List<Double> systemTimeReferenceGap = new ArrayList<>();
            long totalTimeReferenceGap = 0;
            for (ExperimentTimeReference.TimeMapping tm : experimentTimeReference.getTimeMappings()) {
                if (tm.event == ExperimentTimeReference.TimeMappingEvent.START) {
                    starts.add(tm.experimentTime);
                    stops.add(trStop);
                    if (!Double.isNaN(trStop))
                        totalTimeReferenceGap += tm.systemTime - trStopSystemTime;
                    systemTimeReferenceGap.add(totalTimeReferenceGap*0.001);
                    trStop = Double.NaN;
                } else if (tm.event == ExperimentTimeReference.TimeMappingEvent.PAUSE) {
                    trStop = tm.experimentTime;
                    trStopSystemTime = tm.systemTime;
                }
            }
            if (!Double.isNaN(trStop)) {
                starts.add(Double.NaN);
                stops.add(trStop);
            }
            gv.setTimeRanges(starts, stops, systemTimeReferenceGap);
        }
    }

    @Override
    public void onTimeReferenceUpdate(ExperimentTimeReference experimentTimeReference) {
        setTimeReferences(experimentTimeReference);
    }

    @Override
    //Return a javascript function which stores the x data array for later use
    public String setDataHTML() {
        StringBuilder sb = new StringBuilder();
        sb.append("function (data) {");

        sb.append("     elementData[" + htmlID + "][\"datasets\"] = [];");
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i) == null)
                continue;
            sb.append("if (!data.hasOwnProperty(\""+inputs.get(i).replace("\"", "\\\"")+"\"))");
            sb.append("    return;");
            sb.append("elementData["+htmlID+"][\"datasets\"]["+i+"] = data[\""+inputs.get(i).replace("\"", "\\\"")+"\"];");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    //Data complete, let's send it to the graphView
    //Also clear the data afterwards to avoid sending it multiple times if it is not updated for
    //some reason
    public void dataComplete() {
        super.dataComplete();

        if (gv == null)
            return;
        if (dataY[0] != null) {
            if (dataX[0] != null) {
                gv.addGraphData(dataY, dataMinY, dataMaxY, dataX, dataMinX, dataMaxX, dataMinZ, dataMaxZ, timeReferencesX, timeReferencesY);
                dataX[0] = null;
            } else
                gv.addGraphData(dataY, dataMinY, dataMaxY);
            dataY[0] = null;
        }
        if (pickDataChangedExternally && interactiveGV != null) {
            pickDataChangedExternally = false;
            interactiveGV.updatePickData(currentPickData);
        }
    }

    @Override
    //This looks pretty ugly and indeed needs a clean-up...
    //This function returns a javascript function which updates the flot chart.
    //So we have to set-up some JSON objects to define the graph, put it into the JavaScript
    //function (which has to setup some JSON itself) and return the whole nightmare. There
    //certainly is a way to beautify this, but it's not too obvious...
    public String dataCompleteHTML() {
        String rescale = "";
        String scaleX = "";
        if (followX && !Double.isNaN(minX) && !Double.isNaN(maxX)) {
            //Keep the window width from minX/maxX but anchor its end at the newest x value, like GraphView.rescale()
            scaleX += "\"min\":" + minX + ", \"max\":" + maxX + ", ";
            rescale += "if (elementData["+htmlID+"][\"datasets\"][0][\"data\"].length > 0) {";
            rescale += "elementData["+htmlID+"][\"graph\"].options.scales.xAxes[0].ticks.max = maxX;";
            rescale += "elementData["+htmlID+"][\"graph\"].options.scales.xAxes[0].ticks.min = maxX - " + (maxX - minX) + ";";
            rescale += "}";
        } else {
            if (scaleMinX == GraphView.ScaleMode.fixed && !Double.isNaN(minX))
                scaleX += "\"min\":" + minX + ", ";
            else
                rescale += "elementData["+htmlID+"][\"graph\"].options.scales.xAxes[0].ticks.min = minX;";
            if (scaleMaxX == GraphView.ScaleMode.fixed && !Double.isNaN(maxX))
                scaleX += "\"max\":" + maxX + ", ";
            else
                rescale += "elementData["+htmlID+"][\"graph\"].options.scales.xAxes[0].ticks.max = maxX;";
        }
        String scaleY = "";
        if (scaleMinY == GraphView.ScaleMode.fixed && !Double.isNaN(minY))
            scaleY += "\"min\":" + minY + ", ";
        else
            rescale += "elementData["+htmlID+"][\"graph\"].options.scales.yAxes[0].ticks.min = minY;";
        if (scaleMaxY == GraphView.ScaleMode.fixed && !Double.isNaN(maxY))
            scaleY += "\"max\":" + maxY + ", ";
        else
            rescale += "elementData["+htmlID+"][\"graph\"].options.scales.yAxes[0].ticks.max = maxY;";

        String scaleZ = "";
        String colorScale = "[";
        if (this.style.get(0) == GraphView.Style.mapXY) {
            if (scaleMinZ == GraphView.ScaleMode.fixed && !Double.isNaN(minZ))
                scaleZ += "minZ = " + minZ + ";";
            if (scaleMaxZ == GraphView.ScaleMode.fixed && !Double.isNaN(maxZ))
                scaleZ += "maxZ = " + maxZ + ";";
            scaleZ += "elementData["+htmlID+"][\"graph\"].logZ = " + (this.logZ ? "true" : "false") + ";";
            scaleZ += "elementData["+htmlID+"][\"graph\"].minZ = minZ;";
            scaleZ += "elementData["+htmlID+"][\"graph\"].maxZ = maxZ;";

            boolean first = true;
            for (Integer color : this.gv.graphSetup.colorScale) {
                if (first)
                    first = false;
                else
                    colorScale += ",";
                colorScale += (color & 0xffffffffL);
            }
        }
        colorScale += "]";

        final String type = this.style.get(0) == GraphView.Style.mapXY ? "colormap" : "scatter";

        String styleDetection = "switch (i/2) {";
        String graphSetup = "[";
        for (int i = 0; i < inputs.size(); i+=2) {

            graphSetup +=   "{"+
                                "type: \"" + type +"\"," +
                                "showLine: "+ (style.get(i/2) == GraphView.Style.dots || style.get(i/2) == GraphView.Style.mapXY ? "false" : "true") +"," +
                                "fill: "+(style.get(i/2) == GraphView.Style.vbars || style.get(i/2) == GraphView.Style.hbars ? "\"origin\"" : "false")+"," +
                                "pointRadius: "+ (style.get(i/2) == GraphView.Style.dots ? 2.0*lineWidth.get(i/2) : 0) +"*scaleFactor," +
                                "pointHitRadius: "+ (4.0*lineWidth.get(i/2)) +"*scaleFactor," +
                                "pointHoverRadius: "+ (4.0*lineWidth.get(i/2)) +"*scaleFactor," +
                                "lineTension: 0," +
                                "borderCapStyle: \"butt\"," +
                                "borderJoinStyle: \"round\"," +
                                "spanGaps: false," +
                                "borderColor: adjustableColor(\"#" + String.format("%08x", color.get(i/2).intColor()).substring(2) + "\")," +
                                "backgroundColor: adjustableColor(\"#" + String.format("%08x", color.get(i/2).intColor()).substring(2) + "\")," +
                                "borderWidth: " + (style.get(i/2) == GraphView.Style.vbars || style.get(i/2) == GraphView.Style.hbars ? 0.0 : lineWidth.get(i/2)) +
                                "*scaleFactor," +
                                "xAxisID: \"xaxis\"," +
                                "yAxisID: \"yaxis\"" +
                            "},";

            styleDetection += "case " + (i/2) + ": type = \"" + style.get(i/2) + "\"; lineWidth = " + lineWidth.get(i / 2) + "*scaleFactor; break;";
        }
        styleDetection += "}";
        graphSetup += "],";

        return "function () {" +
                    "if (elementData["+htmlID+"][\"datasets\"].length < 1)" +
                        "return;" +
                    "var changed = false;" +
                    "for (var i = 0; i < elementData["+htmlID+"][\"datasets\"].length; i++) {" +
                        "if (elementData["+htmlID+"][\"datasets\"][i][\"changed\"])" +
                            "changed = true;" +
                    "}" +
                    "if (!changed)" +
                        "return;" +
                    "var d = [];" +
                    "var minX = Number.POSITIVE_INFINITY; " +
                    "var maxX = Number.NEGATIVE_INFINITY; " +
                    "var minY = Number.POSITIVE_INFINITY; " +
                    "var maxY = Number.NEGATIVE_INFINITY; " +
                    "var minZ = Number.POSITIVE_INFINITY; " +
                    "var maxZ = Number.NEGATIVE_INFINITY; " +
                    "for (var i = 0; i < elementData["+htmlID+"][\"datasets\"].length; i+=2) {" +
                        "d[i/2] = [];" +
                        "var xIndexed = ((i+1 >= elementData["+htmlID+"][\"datasets\"].length) || elementData["+htmlID+"][\"datasets\"][i+1][\"data\"].length == 0);" +
                        "var type;" +
                        "var lineWidth;" +
                        styleDetection +
                        "if (type == \""+ GraphView.Style.mapZ+"\" || (type == \""+ GraphView.Style.mapXY+"\" && elementData["+htmlID+"][\"datasets\"].length < i+2)) {" +
                            "continue;" +
                        "}" +
                        "var lastX = false;" +
                        "var lastY = false;" +
                        "var nElements = elementData["+htmlID+"][\"datasets\"][i][\"data\"].length;" +
                        "if (!xIndexed)" +
                        "   nElements = Math.min(nElements, elementData[" + htmlID + "][\"datasets\"][i+1][\"data\"].length);" +
                        "if (type == \""+ GraphView.Style.mapXY+"\")" +
                            "nElements = Math.min(nElements, elementData[" + htmlID + "][\"datasets\"][i+2][\"data\"].length);" +
                        "for (j = 0; j < nElements; j++) {" +
                            "var x = xIndexed ? j : elementData["+htmlID+"][\"datasets\"][i+1][\"data\"][j];"+
                            "var y = elementData[" + htmlID + "][\"datasets\"][i][\"data\"][j];" +
                            "if (x < minX)" +
                            "    minX = x;" +
                            "if (x > maxX)" +
                            "    maxX = x;" +
                            "if (y < minY)" +
                            "    minY = y;" +
                            "if (y > maxY)" +
                            "    maxY = y;" +
                            "if (type == \""+ GraphView.Style.vbars+"\") {" +
                                "if (lastX !== false && lastY !== false) {"+
                                    "var offset = (x-lastX)*(1.0-lineWidth)/2.;" +
                                    "d[i/2][j*3+0] = {x: lastX+offset, y: lastY};" +
                                    "d[i/2][j*3+1] = {x: x-offset, y: lastY};" +
                                    "d[i/2][j*3+2] = {x: NaN, y: NaN};" +
                                "}"+
                            "} else if (type == \""+ GraphView.Style.hbars+"\") {" +
                                "if (lastX !== false && lastY !== false) {"+
                                    "var offset = (y-lastX)*(1.0-lineWidth)/2.;" +
                                    "d[i/2][j*3+0] = {x: lastX, y: lastY+offset};" +
                                    "d[i/2][j*3+1] = {x: lastX, y: y-offset};" +
                                    "d[i/2][j*3+2] = {x: NaN, y: NaN};" +
                                "}"+
                            "} else if (type == \""+ GraphView.Style.mapXY+"\") {" +
                                "var z = elementData[" + htmlID + "][\"datasets\"][i+2][\"data\"][j];" +
                                "if (z < minZ)" +
                                "    minZ = z;" +
                                "if (z > maxZ)" +
                                "    maxZ = z;" +
                                "d[i/2][j] = {x: x, y: y, z: z};" +
                            "} else {" +
                                "d[i/2][j] = {x: x, y: y};" +
                            "}" +
                            "lastX = x;" +
                            "lastY = y;" +
                        "}" +

                    "}" +
                    "if (minX > maxX) {" +
                        "minX = 0;" +
                        "maxX = 1;" +
                    "}" +
                    "if (minY > maxY) {" +
                        "minY = 0;" +
                        "maxY = 1;" +
                    "}" +
                    "if (minZ > maxZ) {" +
                        "minZ = 0;" +
                        "maxZ = 1;" +
                    "}" +

                    "if (!elementData["+htmlID+"][\"graph\"]) {" +
                        "var ctx = document.getElementById(\"element"+htmlID+"\").getElementsByClassName(\"graph\")[0].getElementsByTagName(\"canvas\")[0];" +
                        "elementData["+htmlID+"][\"graph\"] = new Chart(ctx, {" +
                            "type: \"" + type + "\"," +
                            "mapwidth: "+this.mapWidth.get(0)+"," +
                            "colorscale: " + colorScale + "," +
                            "data: {datasets: "+
                                graphSetup +
                            "}," +
                            "options: {" +
                                "responsive: true, " +
                                "maintainAspectRatio: false, " +
                                "animation: false," +
                                "legend: false," +
                                "tooltips: {" +
                                "    titleFontSize: 15*scaleFactor," +
                                "    bodyFontSize: 15*scaleFactor," +
                                "    mode: \"nearest\"," +
                                "    intersect: " + (this.style.get(0) == GraphView.Style.mapXY ? "false" : "true") + "," +
                                    "callbacks: {" +
                                    "   title: function() {}," +
                                    "   label: function(tooltipItem, data) {" +
                                    "       var lines = [];" +
                                    "       lines.push(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].x + \""+this.unitX + "\");" +
                                    "       lines.push(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].y + \""+this.unitY + "\");" +
                                    (this.style.get(0) == GraphView.Style.mapXY ? "lines.push(data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index].z + \""+this.unitZ + "\");" : "") +
                                    "       return lines;" +
                                    "   }" +
                                    "}" +
                                "}," +
                                "hover: {" +
                                "    mode: \"nearest\"," +
                                "    intersect: " + (this.style.get(0) == GraphView.Style.mapXY ? "false" : "true") + "," +
                                "}, " +
                                "scales: {" +
                                    "xAxes: [{" +
                                        "id: \"xaxis\"," +
                                        "type: \""+(logX && !(this.style.get(0) == GraphView.Style.mapXY) ? "logarithmic" : "linear")+"\"," +
                                        "position: \"bottom\"," +
                                        "gridLines: {" +
                                            "color: adjustableColor(\"#"+gridColor+"\")," +
                                            "zeroLineColor: adjustableColor(\"#"+gridColor+"\")," +
                                            "tickMarkLength: 0," +
                                        "}," +
                                        "scaleLabel: {" +
                                            "display: true," +
                                            "labelString: \""+this.labelX+(this.unitX != null && !this.unitX.isEmpty() ? " (" + this.unitX + ")" : "")+"\"," +
                                            "fontColor: adjustableColor(\"#ffffff\")," +
                                            "fontSize: 15*scaleFactor," +
                                            "padding: 0, "+
                                        "}," +
                                        "ticks: {" +
                                            "fontColor: adjustableColor(\"#ffffff\")," +
                                            "fontSize: 15*scaleFactor," +
                                            "padding: 3*scaleFactor, "+
                                            "autoSkip: true," +
                                            "maxTicksLimit: 10," +
                                            "maxRotation: 0," +
                                            scaleX+
                                        "}," +
                                        "afterBuildTicks: filterEdgeTicks" +
                                    "}]," +
                                    "yAxes: [{" +
                                        "id: \"yaxis\"," +
                                        "type: \""+(logX && !(this.style.get(0) == GraphView.Style.mapXY) ? "logarithmic" : "linear")+"\"," +
                                        "position: \"bottom\"," +
                                        "gridLines: {" +
                                            "color: adjustableColor(\"#"+gridColor+"\")," +
                                            "zeroLineColor: adjustableColor(\"#"+gridColor+"\")," +
                                            "tickMarkLength: 0," +
                                        "}," +
                                        "scaleLabel: {" +
                                            "display: true," +
                                            "labelString: \""+this.labelY+(this.unitY != null && !this.unitY.isEmpty() ? " (" + this.unitY + ")" : "")+"\"," +
                                            "fontColor: adjustableColor(\"#ffffff\")," +
                                            "fontSize: 15*scaleFactor," +
                                            "padding: 3*scaleFactor, "+
                                        "}," +
                                        "ticks: {" +
                                            "fontColor: adjustableColor(\"#ffffff\")," +
                                            "fontSize: 15*scaleFactor," +
                                            "padding: 3*scaleFactor, "+
                                            "autoSkip: true," +
                                            "maxTicksLimit: 7," +
                                            scaleY+
                                        "}," +
                                        "afterBuildTicks: filterEdgeTicks" +
                                "   }]," +
                                "}" +
                            "}" +
                        "});" +
                    "}" +
                    "for (var i = 0; i < elementData["+htmlID+"][\"datasets\"].length; i+=2) {" +
                        "elementData["+htmlID+"][\"graph\"].data.datasets[i/2].data = d[i/2];" +
                    "}" +
                    scaleZ +
                    rescale +
                    "elementData["+htmlID+"][\"graph\"].update();" +
                "}";
    }

    @Override
    protected void clear() {

    }

    @Override
    public void restore() {
        super.restore();
        if (rootView != null && interactiveGV != null && parent != null) {
            isExclusive = false;
            interactiveGV.prepareExclusive(false);

            interactiveGV.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            interactiveGV.requestLayout();

            interactiveGV.setInteractive(false);

        }
    }

    @Override
    public void maximize() {
        super.maximize();
        if (rootView != null && interactiveGV != null && parent != null) {
            isExclusive = true;
            interactiveGV.prepareExclusive(true);

            interactiveGV.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            interactiveGV.requestLayout();

            interactiveGV.setInteractive(true);
        }
    }

    //Apply zoom to all graphs on the current page.
    //If min or max are NaN, they are reset
    //Follow is only allowed for x-axis if partialUpdate is set
    //If unit AND buffer are null, the zoom is applied to the same axis on all graphs
    //If unit is set, it is applied to all axes with the same unit on all graphs
    //If buffer is set, it is applied to all axes with the same buffer on all graphs
    public void applyZoom(double min, double max, boolean follow, String unit, String buffer, boolean yAxis, boolean absoluteTime) {
        if (unit != null) {
            if (unitX.equals(unit)) {
                zoomState.minX = min;
                zoomState.maxX = max;
                zoomState.follows = follow;
                if (timeOnX)
                    gv.setAbsoluteTime(absoluteTime);
            }
            if (unitY.equals(unit)) {
                zoomState.minY = min;
                zoomState.maxY = max;
                if (timeOnY)
                    gv.setAbsoluteTime(absoluteTime);
            }
        } else if (buffer != null) {
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) != null && inputs.get(i).equals(buffer)) {
                    if (i % 2 == 1) {
                        zoomState.minX = min;
                        zoomState.maxX = max;
                        zoomState.follows = follow;
                        if (timeOnX)
                            gv.setAbsoluteTime(absoluteTime);
                    } else {
                        zoomState.minY = min;
                        zoomState.maxY = max;
                        if (timeOnY)
                            gv.setAbsoluteTime(absoluteTime);
                    }
                }
            }
        } else {
            if (!yAxis) {
                zoomState.minX = min;
                zoomState.maxX = max;
                zoomState.follows = follow;
                if (timeOnX)
                    gv.setAbsoluteTime(absoluteTime);
            } else {
                zoomState.minY = min;
                zoomState.maxY = max;
                if (timeOnY)
                    gv.setAbsoluteTime(absoluteTime);
            }
        }
        gv.zoomState = zoomState;
        gv.rescale();
        gv.invalidate();
    }

}
