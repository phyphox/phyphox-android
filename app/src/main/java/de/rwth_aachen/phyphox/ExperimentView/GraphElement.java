package de.rwth_aachen.phyphox.ExperimentView;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    //Fixed plot area as fractions of the element's box (file format 1.21); NaN = laid out automatically around the labels
    double plotLeft = Double.NaN, plotTop = Double.NaN, plotRight = Double.NaN, plotBottom = Double.NaN;

    GraphView.ZoomState zoomState = null;

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

    public void setPlotArea(double left, double top, double right, double bottom) {
        this.plotLeft = left;
        this.plotTop = top;
        this.plotRight = right;
        this.plotBottom = bottom;
        if (gv != null)
            gv.setPlotArea(left, top, right, bottom);
    }

    public boolean hasFixedPlotArea() {
        return !(Double.isNaN(plotLeft) && Double.isNaN(plotTop) && Double.isNaN(plotRight) && Double.isNaN(plotBottom));
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
        //Inside a stack the graph is static (no maximize, zoom or picks) and its plot is transparent, so what lies below shows through
        interactiveGV.setStatic(inStack);
        interactiveGV.setTransparentPlot(inStack);
        //With a fixed plot area the fractions refer to the whole element, so the label moves into the plot's top margin
        interactiveGV.setFixedPlotArea(hasFixedPlotArea());
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
        gv.setPlotArea(plotLeft, plotTop, plotRight, plotBottom);
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

        if (!inStack) {
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
        }

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
    //The remote interface builds the graph itself from the configuration returned by
    //getWebGraphConfig(), so there is no markup to generate here.
    protected String createViewHTML(){
        return "";
    }

    //#rrggbb, or #rrggbbaa for a color with an alpha byte (file format 1.21)
    private static String hexColor(int argb) {
        return "#" + RGB.fromARGB(argb).hexString();
    }

    //JSONObject.put drops a key for a null value, but the contract says every key is present
    private static Object jsonString(String s) {
        return s == null ? JSONObject.NULL : s;
    }

    @Override
    //Everything the remote interface needs to know about this graph as JSON: the datasets with
    //their buffers and styles, axis labels, ranges and the data picker outputs. The interface
    //(phyphox-webinterface, index.html) turns this into a Chart.js chart. The structure is
    //documented in the webinterface's readme.md and must stay in step with iOS.
    public String getWebGraphConfig() {
        try {
            JSONObject cfg = new JSONObject();
            cfg.put("aspectRatio", aspectRatio);
            cfg.put("labelX", jsonString(labelX));
            cfg.put("labelY", jsonString(labelY));
            cfg.put("labelZ", jsonString(labelZ));
            cfg.put("unitX", jsonString(unitX));
            cfg.put("unitY", jsonString(unitY));
            cfg.put("unitZ", jsonString(unitZ));
            cfg.put("unitYX", jsonString(unitYX));
            cfg.put("logX", logX);
            cfg.put("logY", logY);
            cfg.put("logZ", logZ);
            cfg.put("xPrecision", xPrecision);
            cfg.put("yPrecision", yPrecision);
            cfg.put("zPrecision", zPrecision);
            cfg.put("suppressScientificNotation", suppressScientificNotation);
            cfg.put("timeOnX", timeOnX);
            cfg.put("timeOnY", timeOnY);
            cfg.put("systemTime", absoluteTime);
            cfg.put("linearTime", linearTime);
            cfg.put("scaleMinX", scaleMinX.name());
            cfg.put("scaleMaxX", scaleMaxX.name());
            cfg.put("scaleMinY", scaleMinY.name());
            cfg.put("scaleMaxY", scaleMaxY.name());
            cfg.put("scaleMinZ", scaleMinZ.name());
            cfg.put("scaleMaxZ", scaleMaxZ.name());
            cfg.put("minX", Double.isNaN(minX) ? JSONObject.NULL : minX);
            cfg.put("maxX", Double.isNaN(maxX) ? JSONObject.NULL : maxX);
            cfg.put("minY", Double.isNaN(minY) ? JSONObject.NULL : minY);
            cfg.put("maxY", Double.isNaN(maxY) ? JSONObject.NULL : maxY);
            cfg.put("minZ", Double.isNaN(minZ) ? JSONObject.NULL : minZ);
            cfg.put("maxZ", Double.isNaN(maxZ) ? JSONObject.NULL : maxZ);
            cfg.put("followX", followX);
            cfg.put("plotLeft", Double.isNaN(plotLeft) ? JSONObject.NULL : plotLeft);
            cfg.put("plotTop", Double.isNaN(plotTop) ? JSONObject.NULL : plotTop);
            cfg.put("plotRight", Double.isNaN(plotRight) ? JSONObject.NULL : plotRight);
            cfg.put("plotBottom", Double.isNaN(plotBottom) ? JSONObject.NULL : plotBottom);
            cfg.put("partialUpdate", partialUpdate);
            cfg.put("mapWidth", mapWidth.get(0));
            cfg.put("showColorScale", showColorScale);
            cfg.put("interpolateMapColors", interpolateMapColors);
            JSONArray scale = new JSONArray();
            for (Integer c : colorScale)
                scale.put(hexColor(c));
            if (scale.length() > 1)
                cfg.put("colorScale", scale);

            //inputs holds (y, x) pairs per curve; a z input is a separate curve of style mapZ that
            //follows its dataset (see PhyphoxFile)
            JSONArray datasets = new JSONArray();
            JSONObject last = null;
            for (int i = 0; i < inputs.size(); i += 2) {
                int curve = i / 2;
                GraphView.Style s = curve < style.size() ? style.get(curve) : GraphView.Style.lines;
                if (s == GraphView.Style.mapZ) {
                    if (last != null)
                        last.put("z", inputs.get(i));
                    continue;
                }
                JSONObject ds = new JSONObject();
                ds.put("y", inputs.get(i));
                ds.put("x", i + 1 < inputs.size() && inputs.get(i + 1) != null ? inputs.get(i + 1) : JSONObject.NULL);
                ds.put("z", JSONObject.NULL);
                ds.put("style", s == GraphView.Style.mapXY ? "map" : s.name());
                ds.put("lineWidth", curve < lineWidth.size() ? lineWidth.get(curve) : 1.0);
                ds.put("color", hexColor((curve < color.size() ? color.get(curve) : color.get(0)).intColor()));
                datasets.put(ds);
                last = ds;
            }
            cfg.put("datasets", datasets);

            cfg.put("pickLabel", jsonString(pickLabel));
            //Outputs come in (value, assigned value) pairs cycling through the x, y and z axes
            JSONArray picks = new JSONArray();
            if (outputs != null) {
                final String[] axes = {"x", "y", "z"};
                for (int i = 0; i < outputs.size(); i += 2) {
                    DataOutput out = outputs.get(i);
                    if (out == null || out.buffer == null)
                        continue;
                    DataOutput cal = i + 1 < outputs.size() ? outputs.get(i + 1) : null;
                    JSONObject pick = new JSONObject();
                    pick.put("axis", axes[(i / 2) % 3]);
                    pick.put("buffer", out.buffer.name);
                    pick.put("label", jsonString(out.label));
                    pick.put("calBuffer", cal != null && cal.buffer != null ? cal.buffer.name : JSONObject.NULL);
                    pick.put("calLabel", cal != null && cal.buffer != null ? jsonString(cal.label) : JSONObject.NULL);
                    picks.put(pick);
                }
            }
            cfg.put("pickOutputs", picks);
            return cfg.toString();
        } catch (JSONException e) {
            return null;
        }
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
        if (inStack)
            return;
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
