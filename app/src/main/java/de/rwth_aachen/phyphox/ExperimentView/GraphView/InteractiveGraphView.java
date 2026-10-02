package de.rwth_aachen.phyphox.ExperimentView.GraphView;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;


import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.DataExport;
import de.rwth_aachen.phyphox.DataOutput;
import de.rwth_aachen.phyphox.ExpViewFragment;
import de.rwth_aachen.phyphox.ExperimentView.MarkerOverlayView.MarkerOverlayView;
import de.rwth_aachen.phyphox.R;
import de.rwth_aachen.phyphox.Unit;
import de.rwth_aachen.phyphox.UnitDialog;
import de.rwth_aachen.phyphox.helper.Helper;

public class InteractiveGraphView extends RelativeLayout implements GraphView.PointInfo {

    private boolean interactive = false;
    private boolean linearRegression = false;
    public GraphView graphView;
    private PlotAreaView plotAreaView;
    private boolean fixedPlotArea = false;
    private boolean titleRow = true; //label and expand icon above the plot; gone inside a stack, with a fixed plot area or without a label
    private TextView graphLabel;
    private ImageView expandImage, collapseImage;
    private LinearLayoutCompat toolbar;
    int selectedItem = R.id.graph_tools_pan;
    boolean isHorizontalLayout = false;
    public boolean allowLogX = false;
    public boolean allowLogY = false;

    private PlotRenderer plotRenderer = null;

    private DataExport dataExport = null;

    String pickLabel = null;
    Vector<DataOutput> outputs = null;
    Double[] pickData = null;
    public interface PickerObserver {
        void onPick(Double[] data);
    }
    PickerObserver observer = null;

    //Tells the GraphElement about a unit switched through the axis dialog, so it survives re-creation of the view
    public interface DisplayUnitObserver {
        void onDisplayUnitChanged(int axis, String id);
    }
    DisplayUnitObserver displayUnitObserver = null;

    View rootView;
    FrameLayout graphFrame;

    private class Marker {
        boolean active = false;
        float viewX, viewY;
        float dataX, dataY, dataZ;

        Marker() {
        }

        public void remove() {
            active = false;
            updateInfo();
        }

        public void set(float viewX, float viewY, float dataX, float dataY, float dataZ) {
            linearRegression = false;

            active = true;
            this.viewX = viewX;
            this.viewY = viewY;
            this.dataX = dataX;
            this.dataY = dataY;
            this.dataZ = dataZ;

            updateInfo();
        }
    }

    final int markerMax = 2;
    Marker marker[] = new Marker[markerMax];
    public PopupWindow popupWindowInfo = null;
    public TextView popupWindowText = null;
    MarkerOverlayView markerOverlayView;

    public InteractiveGraphView(Context context) {
        super(context);
        init(context);
    }

    public InteractiveGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void selectItem(int item) {
        selectedItem = item;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            ImageView iv = child.findViewById(R.id.item_icon);
            TextView tv = child.findViewById(R.id.item_title);
            if (iv != null && tv != null) {
                iv.setSelected(child.getId() == selectedItem);
                tv.setSelected(child.getId() == selectedItem);
            }
        }
    }
    private void updateMenuLayout(Boolean isHorizontal) {

        // Toolbar
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) toolbar.getLayoutParams();
        if (isHorizontal) {
            lp.height = RelativeLayout.LayoutParams.MATCH_PARENT;
            lp.width = RelativeLayout.LayoutParams.WRAP_CONTENT;
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.addRule(RelativeLayout.BELOW, R.id.graph_label);
        } else {
            lp.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            lp.removeRule(RelativeLayout.BELOW);
            lp.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
            lp.width = RelativeLayout.LayoutParams.MATCH_PARENT;
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        }
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        lp.topMargin = isHorizontal ? 0 : margin;
        lp.leftMargin = isHorizontal ? margin : 0;
        toolbar.setOrientation(isHorizontal ? LinearLayoutCompat.VERTICAL : LinearLayoutCompat.HORIZONTAL);
        toolbar.setLayoutParams(lp);

        // Graph
        RelativeLayout.LayoutParams lpFrame = (RelativeLayout.LayoutParams) graphFrame.getLayoutParams();
        if (isHorizontal) {
            lpFrame.removeRule(RelativeLayout.ABOVE);
            lpFrame.addRule(RelativeLayout.BELOW, R.id.graph_label);
            lpFrame.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            lpFrame.addRule(RelativeLayout.LEFT_OF, R.id.graph_toolbar);
            lpFrame.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        } else {
            lpFrame.removeRule(RelativeLayout.LEFT_OF);
            lpFrame.addRule(RelativeLayout.BELOW, R.id.graph_label);
            lpFrame.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            lpFrame.addRule(RelativeLayout.ABOVE, R.id.graph_toolbar);
            lpFrame.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        }
        graphFrame.setLayoutParams(lpFrame);

        // Menu items
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();

            if (isHorizontal) {
                params.width = LinearLayout.LayoutParams.MATCH_PARENT;
                params.height = 0;
                params.weight = 1.0f;
            } else {
                params.width = 0;
                params.height = LinearLayout.LayoutParams.MATCH_PARENT;
                params.weight = 1.0f;
            }
            child.setLayoutParams(params);
        }
    }

    private void init(Context context) {
        rootView = inflate(context, R.layout.interactive_graph_layout, this);

        for (int i = 0; i < markerMax; i++)
            marker[i] = new Marker();

        graphFrame = (FrameLayout)this.findViewById(R.id.graph_frame);
        graphLabel = (TextView)this.findViewById(R.id.graph_label);
        expandImage = (ImageView)this.findViewById(R.id.graph_expand_image);
        collapseImage = (ImageView)this.findViewById(R.id.graph_collapse_image);
        toolbar = (LinearLayoutCompat) this.findViewById(R.id.graph_toolbar);
        setExpandCollapseImageColor(context);

        // Because of edge to edge feature from Android 15, bottom nav bar need to be handled as the bottom padding for this will be automatically set
        // Removing the bottom navigation bar inset, as this inset is already applied in its main class, i.e Experiment class
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.graph_toolbar), (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return insets;
        });

        PopupMenu tempMenu = new PopupMenu(context, toolbar);
        tempMenu.getMenuInflater().inflate(R.menu.graph_menu, tempMenu.getMenu());
        Menu menu = tempMenu.getMenu();
        toolbar.removeAllViews();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem menuItem = menu.getItem(i);

            View itemView = LayoutInflater.from(context).inflate(R.layout.interactive_graph_menu_item, toolbar, false);

            ImageView iconView = itemView.findViewById(R.id.item_icon);
            TextView textView = itemView.findViewById(R.id.item_title);

            iconView.setImageDrawable(menuItem.getIcon());
            textView.setText(menuItem.getTitle());
            itemView.setId(menuItem.getItemId()); // Keep your original menu item ID!

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int itemId = view.getId();
                    if (itemId == R.id.graph_tools_pan) {
                        selectItem(itemId);
                        graphView.setTouchMode(GraphView.TouchMode.zoom);
                    } else if (itemId == R.id.graph_tools_pick) {
                        selectItem(itemId);
                        graphView.setTouchMode(GraphView.TouchMode.pick);
                    } else if (itemId == R.id.graph_tools_more) {
                        PopupMenu popup = createGraphToolPopUpMenu();
                        popup.setOnMenuItemClickListener(menuItem -> {
                            int id = menuItem.getItemId();
                            if (id == R.id.graph_tools_linear_fit) {
                                linearRegression = !linearRegression;
                                graphView.resetPicks();
                                updateInfo();
                            } else if (id == R.id.graph_tools_system_time) {
                                graphView.setAbsoluteTime(!graphView.absoluteTime);
                                graphView.invalidate();
                            }
                            else if (id == R.id.graph_tools_reset) {
                                graphView.zoomState.follows = graphView.followX;
                                if (graphView.followX) {
                                    graphView.zoomState.minX = graphView.minX;
                                    graphView.zoomState.maxX = graphView.maxX;
                                } else {
                                    graphView.zoomState.minX = Double.NaN;
                                    graphView.zoomState.maxX = Double.NaN;
                                }
                                graphView.zoomState.minY = Double.NaN;
                                graphView.zoomState.maxY = Double.NaN;
                                graphView.zoomState.minZ = Double.NaN;
                                graphView.zoomState.maxZ = Double.NaN;
                                graphView.invalidate();
                            }
                            else if (id == R.id.graph_tools_follow) {
                                if (Double.isNaN(graphView.zoomState.minX) || Double.isNaN(graphView.zoomState.maxX)) {
                                    graphView.zoomState.minX = graphView.minX;
                                    graphView.zoomState.maxX = graphView.maxX;
                                }
                                graphView.zoomState.follows = !graphView.zoomState.follows;
                                graphView.invalidate();
                            }
                            else if (id == R.id.graph_tools_export) {
                                Context ctx = getContext();
                                Activity act = null;
                                while (ctx instanceof ContextWrapper) {
                                    if (ctx instanceof Activity) {
                                        act = (Activity) ctx;
                                    }
                                    ctx = ((ContextWrapper) ctx).getBaseContext();
                                }
                                if (act != null)
                                    dataExport.export(act, true);
                            }
                            else if (id == R.id.graph_tools_log_x) {
                                graphView.setLogScale(!graphView.logX, graphView.logY, graphView.logZ);
                                graphView.invalidate();
                            }
                            else if (id == R.id.graph_tools_log_y) {
                                graphView.setLogScale(graphView.logX, !graphView.logY, graphView.logZ);
                                graphView.invalidate();
                            }
                            return false;
                        });
                        popup.show();
                    }
                }
            });

            toolbar.addView(itemView);
        }

        isHorizontalLayout = getWidth() > getHeight();
        updateMenuLayout(isHorizontalLayout);

        selectItem(R.id.graph_tools_pan);

        plotAreaView = new PlotAreaView(context);
        plotAreaView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        plotRenderer = new PlotRenderer(context);
        plotRenderer.start();
        plotAreaView.setSurfaceTextureListener(plotRenderer);

        this.graphView = new GraphView(context, plotAreaView, plotRenderer);
        graphView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        graphView.setPointInfoListener(this);
        graphView.setAxisTapListener(this::onAxisTap);
        graphView.setOutsideTapListener(() -> { //a tap beside the plot leaves the maximized graph like the collapse icon does
            if (interactive)
                performClick();
        });

        graphFrame.addView(plotAreaView);
        graphFrame.addView(graphView);

        markerOverlayView = new MarkerOverlayView(getContext());
        markerOverlayView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        markerOverlayView.setGraphSetup(graphView.graphSetup);
        graphFrame.addView(markerOverlayView);
    }

    public void setDisplayUnitObserver(DisplayUnitObserver observer) {
        this.displayUnitObserver = observer;
    }

    //A tap on an axis label in exclusive mode: the unit dialog for that axis (units.md, "Switching a unit by hand")
    private void onAxisTap(int axis) {
        if (!interactive || !graphView.isAxisConvertible(axis))
            return;
        UnitDialog.show(getContext(), graphView.getUnitId(axis), graphView.getDisplayUnitId(axis), id -> setDisplayUnit(axis, id));
    }

    //What the unit dialog does for an axis
    public void setDisplayUnit(int axis, String id) {
        graphView.setDisplayUnit(axis, id);
        if (displayUnitObserver != null)
            displayUnitObserver.onDisplayUnitChanged(axis, graphView.getDisplayUnitId(axis));
        updateInfo();
    }

    //The unit of a slope read-out: unitYperX while both axes show their experiment units, else composed from the
    //display symbols (units.md, "Slopes")
    private String slopeUnit() {
        if (!graphView.isConverted(GraphView.AXIS_X) && !graphView.isConverted(GraphView.AXIS_Y) && graphView.getUnitYX() != null)
            return graphView.getUnitYX();
        String uy = graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? graphView.getUnitY() : "";
        String ux = graphView.getUnitX() != null && !graphView.getUnitX().isEmpty() ? graphView.getUnitX() : "";
        if (ux.isEmpty())
            return uy.isEmpty() ? "" : " " + uy;
        return " " + (uy.isEmpty() ? "1" : uy) + " / " + ux;
    }

    private PopupMenu createGraphToolPopUpMenu(){
        PopupMenu popup = new PopupMenu(getContext(), findViewById(R.id.graph_tools_more));
        popup.getMenuInflater().inflate(R.menu.graph_tools_menu, popup.getMenu());
        popup.getMenu().findItem(R.id.graph_tools_system_time).setVisible((graphView.timeOnX || graphView.timeOnY) && graphView.style[0] != GraphView.Style.mapXY);
        popup.getMenu().findItem(R.id.graph_tools_system_time).setChecked(graphView.absoluteTime);
        popup.getMenu().findItem(R.id.graph_tools_follow).setChecked(graphView.zoomState.follows);
        popup.getMenu().findItem(R.id.graph_tools_follow).setVisible(graphView.graphSetup.incrementalX);
        popup.getMenu().findItem(R.id.graph_tools_export).setVisible(dataExport != null);
        popup.getMenu().findItem(R.id.graph_tools_log_x).setVisible(allowLogX);
        popup.getMenu().findItem(R.id.graph_tools_log_y).setVisible(allowLogY);
        popup.getMenu().findItem(R.id.graph_tools_log_x).setChecked(graphView.logX);
        popup.getMenu().findItem(R.id.graph_tools_log_y).setChecked(graphView.logY);
        boolean hasMap = false;
        for (GraphView.Style style : graphView.style)
            if (style == GraphView.Style.mapXY)
                hasMap = true;
        popup.getMenu().findItem(R.id.graph_tools_linear_fit).setVisible(!(allowLogX || allowLogY || hasMap));
        popup.getMenu().findItem(R.id.graph_tools_linear_fit).setChecked(linearRegression);
        return popup;

    }

    private void removePopUpAndMarkerOverlayView(){
        removePopupInfo();
        markerOverlayView.update(null, null);
    }

    private void setExpandCollapseImageColor(Context context) {
        if(Helper.isDarkTheme(getResources())){
            ImageViewCompat.setImageTintList(collapseImage, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.phyphox_white_100)));
            ImageViewCompat.setImageTintList(expandImage, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.phyphox_white_100)));
        } else {
            ImageViewCompat.setImageTintList(collapseImage, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.phyphox_black_100)));
            ImageViewCompat.setImageTintList(expandImage, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.phyphox_black_100)));
        }
    }

    public void assignDataExporter(DataExport dataExport) {
        this.dataExport = dataExport;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wSize = MeasureSpec.getSize(widthMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);

        if (wSize > 0 && hSize > 0) {
            boolean isHorizontal = wSize > hSize;
            if (isHorizontal != isHorizontalLayout) {
                isHorizontalLayout = isHorizontal;
                updateMenuLayout(isHorizontal);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    //"Keep this view?" when the maximized graph is left with a zoom (ApplyZoomChoice holds the rules). Two direct buttons,
    //"More options…" expands per-axis controls that start from the emphasised button and change the buttons to Cancel/OK.
    public void leaveDialog(final ExpViewFragment parent, final String bufferX, final String bufferY, final Unit unitX, final Unit unitY) {
        final GraphView.ZoomState zoomState = graphView.zoomState;
        if (!ApplyZoomChoice.anyZoomed(zoomState)) {
            parent.leaveExclusive();
            return;
        }
        boolean hasZAxis = false;
        for (GraphView.Style style : graphView.style)
            if (style == GraphView.Style.mapZ)
                hasZAxis = true;
        final boolean[] zoomed = {ApplyZoomChoice.isZoomed(zoomState, GraphView.AXIS_X), ApplyZoomChoice.isZoomed(zoomState, GraphView.AXIS_Y), hasZAxis && ApplyZoomChoice.isZoomed(zoomState, GraphView.AXIS_Z)};
        final boolean incrementalX = graphView.graphSetup.incrementalX;

        final View dialogView = inflate(getContext(), R.layout.apply_zoom_dialog, null);
        final TextView[] labels = {dialogView.findViewById(R.id.applyZoomXLabel), dialogView.findViewById(R.id.applyZoomYLabel), dialogView.findViewById(R.id.applyZoomZLabel)};
        final View[] sections = {dialogView.findViewById(R.id.applyZoomX), dialogView.findViewById(R.id.applyZoomY), dialogView.findViewById(R.id.applyZoomZ)};
        for (int axis = 0; axis < 3; axis++) {
            sections[axis].setVisibility(zoomed[axis] ? VISIBLE : GONE);
            if (zoomed[axis])
                labels[axis].setText(graphView.zoomRangeLine(axis)); //the axis section is headed by its label and range
        }
        final View options = dialogView.findViewById(R.id.applyZoomOptions);
        final RadioButton[] reset = {dialogView.findViewById(R.id.applyZoomXReset), dialogView.findViewById(R.id.applyZoomYReset), dialogView.findViewById(R.id.applyZoomZReset)};
        final RadioButton[] keep = {dialogView.findViewById(R.id.applyZoomXKeep), dialogView.findViewById(R.id.applyZoomYKeep), dialogView.findViewById(R.id.applyZoomZKeep)};
        final RadioButton followX = dialogView.findViewById(R.id.applyZoomXFollow);
        followX.setVisibility(incrementalX ? VISIBLE : GONE);
        final Spinner applyX = dialogView.findViewById(R.id.applyZoomXApplyTo);
        final Spinner applyY = dialogView.findViewById(R.id.applyZoomYApplyTo);
        final List<ApplyZoomChoice.Target> targetsX = setupZoomTargets(applyX, "x", unitX, graphView.getUnitX());
        final List<ApplyZoomChoice.Target> targetsY = setupZoomTargets(applyY, "y", unitY, graphView.getUnitY());

        final ApplyZoomChoice.Action simpleDefault = ApplyZoomChoice.defaultAction(graphView.previouslyKept);
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.applyZoomQuestionTitle)
                .setView(dialogView)
                .setPositiveButton(R.string.applyZoomActionKeep, null)
                .setNegativeButton(R.string.applyZoomActionReset, null)
                .setNeutralButton(R.string.applyZoomMoreOptions, null)
                .create();
        dialog.setOnCancelListener(d -> parent.leaveRequestCancelled());
        dialog.setOnShowListener(d -> {
            final Button keepButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            final Button resetButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            final Button moreButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            (simpleDefault == ApplyZoomChoice.Action.KEEP ? keepButton : resetButton).setTypeface(null, Typeface.BOLD);
            keepButton.setOnClickListener(v -> {
                dialog.dismiss();
                applySimpleZoomChoice(parent, ApplyZoomChoice.Action.KEEP, bufferX, bufferY, unitX, unitY);
            });
            resetButton.setOnClickListener(v -> {
                dialog.dismiss();
                applySimpleZoomChoice(parent, ApplyZoomChoice.Action.RESET, bufferX, bufferY, unitX, unitY);
            });
            moreButton.setOnClickListener(v -> {
                for (int axis = 0; axis < 3; axis++) {
                    ApplyZoomChoice.Action initial = ApplyZoomChoice.initialAxisAction(zoomState, axis, simpleDefault, incrementalX);
                    (initial == ApplyZoomChoice.Action.FOLLOW ? followX : initial == ApplyZoomChoice.Action.KEEP ? keep[axis] : reset[axis]).setChecked(true);
                }
                options.setVisibility(VISIBLE);
                moreButton.setVisibility(GONE);
                keepButton.setText(R.string.ok);
                resetButton.setText(R.string.cancel);
                keepButton.setTypeface(null, Typeface.NORMAL);
                resetButton.setTypeface(null, Typeface.NORMAL);
                resetButton.setOnClickListener(v2 -> dialog.cancel());
                keepButton.setOnClickListener(v2 -> {
                    dialog.dismiss();
                    ApplyZoomChoice.Action[] actions = new ApplyZoomChoice.Action[3];
                    for (int axis = 0; axis < 3; axis++)
                        actions[axis] = !zoomed[axis] || reset[axis].isChecked() ? ApplyZoomChoice.Action.RESET : (axis == GraphView.AXIS_X && followX.isChecked() ? ApplyZoomChoice.Action.FOLLOW : ApplyZoomChoice.Action.KEEP);
                    applyZoomChoice(parent, actions[0], actions[1], actions[2],
                            zoomed[0] ? targetsX.get(applyX.getSelectedItemPosition()) : ApplyZoomChoice.Target.THIS,
                            zoomed[1] ? targetsY.get(applyY.getSelectedItemPosition()) : ApplyZoomChoice.Target.THIS,
                            bufferX, bufferY, unitX, unitY);
                });
            });
        });
        dialog.show();
    }

    //"Also apply to other graphs with…": the same unit is only offered when the axis has one (the symbol shown is the display unit's)
    private List<ApplyZoomChoice.Target> setupZoomTargets(Spinner spinner, String axisName, Unit unit, String unitSymbol) {
        List<ApplyZoomChoice.Target> targets = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        targets.add(ApplyZoomChoice.Target.THIS);
        labels.add(getContext().getString(R.string.applyZoomTargetThis));
        targets.add(ApplyZoomChoice.Target.SAME_DATA);
        labels.add(getContext().getString(R.string.applyZoomTargetSameData));
        if (unit != null && !unit.isEmpty() && unitSymbol != null && !unitSymbol.isEmpty()) {
            targets.add(ApplyZoomChoice.Target.SAME_UNIT);
            labels.add(getContext().getString(R.string.applyZoomTargetSameUnit, unitSymbol));
        }
        targets.add(ApplyZoomChoice.Target.SAME_AXIS);
        labels.add(getContext().getString(R.string.applyZoomTargetSameAxis, axisName));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return targets;
    }

    private void applySimpleZoomChoice(ExpViewFragment parent, ApplyZoomChoice.Action simple, String bufferX, String bufferY, Unit unitX, Unit unitY) {
        GraphView.ZoomState z = graphView.zoomState;
        boolean incrementalX = graphView.graphSetup.incrementalX;
        applyZoomChoice(parent,
                ApplyZoomChoice.initialAxisAction(z, GraphView.AXIS_X, simple, incrementalX),
                ApplyZoomChoice.initialAxisAction(z, GraphView.AXIS_Y, simple, incrementalX),
                ApplyZoomChoice.initialAxisAction(z, GraphView.AXIS_Z, simple, incrementalX),
                ApplyZoomChoice.Target.THIS, ApplyZoomChoice.Target.THIS, bufferX, bufferY, unitX, unitY);
    }

    private void applyZoomChoice(ExpViewFragment parent, ApplyZoomChoice.Action x, ApplyZoomChoice.Action y, ApplyZoomChoice.Action z, ApplyZoomChoice.Target targetX, ApplyZoomChoice.Target targetY, String bufferX, String bufferY, Unit unitX, Unit unitY) {
        ApplyZoomChoice.apply(graphView, x, y, z);
        GraphView.ZoomState s = graphView.zoomState;
        propagateZoom(parent, targetX, s.minX, s.maxX, x == ApplyZoomChoice.Action.FOLLOW, unitX, bufferX, false, graphView.timeOnX && graphView.absoluteTime);
        propagateZoom(parent, targetY, s.minY, s.maxY, false, unitY, bufferY, true, graphView.timeOnY && graphView.absoluteTime);
        parent.leaveExclusive();
    }

    private void propagateZoom(ExpViewFragment parent, ApplyZoomChoice.Target target, double min, double max, boolean follow, Unit unit, String buffer, boolean yAxis, boolean absoluteTime) {
        switch (target) {
            case SAME_DATA: parent.applyZoom(min, max, follow, null, buffer, yAxis, absoluteTime); break;
            case SAME_UNIT: parent.applyZoom(min, max, follow, unit, null, yAxis, absoluteTime); break;
            case SAME_AXIS: parent.applyZoom(min, max, follow, null, null, yAxis, absoluteTime); break;
            default: break;
        }
    }

    //Inside a stack (file format 1.21): no maximize icon and no touches (the stack intercepts them anyway)
    public void setStatic(boolean isStatic) {
        if (isStatic)
            hideTitleRow();
        setClickable(!isStatic);
    }

    //No title row (file format 1.21: a graph without a label has none; also inside a stack and with a fixed plot area)
    private void hideTitleRow() {
        titleRow = false;
        graphLabel.setVisibility(GONE);
        expandImage.setVisibility(GONE);
        collapseImage.setVisibility(GONE);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) graphFrame.getLayoutParams();
        lp.topMargin = 0;
        graphFrame.setLayoutParams(lp);
    }

    //A transparent plot lets the stack's lower children show through the plot area and the margins
    public void setTransparentPlot(boolean transparent) {
        plotAreaView.setOpaque(!transparent);
        plotRenderer.setTransparent(transparent);
        graphView.setTransparentBackground(transparent);
    }

    //With a fixed plot area (plotLeft & co.) the fractions refer to the whole element: the label row above the frame
    //goes away and GraphView draws the label in the plot's top margin if it fits
    public void setFixedPlotArea(boolean fixed) {
        fixedPlotArea = fixed;
        if (fixed)
            hideTitleRow();
    }

    public void prepareExclusive(boolean willBeExclusive) {
        graphView.overrideAspectRatio = willBeExclusive;
        graphView.requestLayout();
    }

    public void setInteractive(boolean interactive) {

        if (!interactive) {
            graphView.setTouchMode(GraphView.TouchMode.off);
            linearRegression = false;
            graphView.resetPicks();
        } else if (selectedItem == R.id.graph_tools_pan)
            graphView.setTouchMode(GraphView.TouchMode.zoom);
        else if (selectedItem == R.id.graph_tools_pick)
            graphView.setTouchMode(GraphView.TouchMode.pick);

        toolbar.setVisibility(interactive ? VISIBLE : GONE);
        if (titleRow) {
            expandImage.setVisibility(interactive ? INVISIBLE : VISIBLE);
            collapseImage.setVisibility(interactive ? VISIBLE : INVISIBLE);
            //The collapse icon is twice the size of the expand icon so it is noticed; the label moves beside it
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) graphLabel.getLayoutParams();
            lp.removeRule(RelativeLayout.RIGHT_OF);
            lp.addRule(RelativeLayout.RIGHT_OF, interactive ? R.id.graph_collapse_image : R.id.graph_expand_image);
            graphLabel.setLayoutParams(lp);
            graphLabel.setGravity(Gravity.CENTER_VERTICAL);
            graphLabel.setMinimumHeight(interactive ? (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 34, getResources().getDisplayMetrics()) : 0);
        }

        this.interactive = interactive;
    }

    public void setLabel(String label) {
        graphLabel.setText(label);
        graphView.setTitle(fixedPlotArea ? label : null);
        if (label == null || label.isEmpty())
            hideTitleRow();
        float selectedTextSize = Helper.getUserSelectedGraphSetting(getContext(), Helper.GraphField.LABEL_SIZE);
        float textSizeAsDisplay = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                selectedTextSize,
                getContext().getResources().getDisplayMetrics());
        graphLabel.setTextSize(textSizeAsDisplay);
    }

    //Outputs come in (value, mapped value) pairs cycling through the x, y and z axes
    private void commitPick(Marker marker, int outputIndex, Double userValue) {
        double markerValue;
        switch ((outputIndex / 2) % 3) {
            case 0: markerValue = marker.dataX; break;
            case 1: markerValue = marker.dataY; break;
            default: markerValue = marker.dataZ; break;
        }
        if (pickData == null)
            pickData = new Double[outputs.size()];
        pickData[outputIndex] = markerValue;
        if (userValue != null)
            pickData[outputIndex+1] = userValue;
        observer.onPick(pickData);
        updatePickMarker();
    }

    private void requestPickMappingValue(Marker pickableMarker, int outputIndex) {
        String title = outputs.get(outputIndex).label;
        String message = outputs.get(outputIndex+1).label;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(title);
        builder.setMessage(message);

        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        if (pickData != null && pickData[outputIndex+1] != null && !pickData[outputIndex+1].isNaN())
            input.setText(String.valueOf(pickData[outputIndex+1]));

        builder.setView(input);

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    double userValue = Double.valueOf(input.getText().toString().replace(",", "."));
                    commitPick(pickableMarker, outputIndex, userValue);
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), R.string.invalidValue, Toast.LENGTH_SHORT).show();
                }
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void updatePopupInfoButtons(Marker pickableMarker) {
        LinearLayout ll = popupWindowInfo.getContentView().findViewById(R.id.pickButtonList);
        if (outputs != null && pickableMarker != null) {
            if (ll.getChildCount() == 0) {
                for (int i = 0; i < outputs.size(); i++) {
                    if (outputs.get(i) == null || (i & 0x01) == 1)
                        continue;
                    final int finalI = i;
                    Button b = new Button(getContext());
                    b.setText(outputs.get(i).label);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (outputs.size() > finalI+1 && outputs.get(finalI+1) != null && outputs.get(finalI+1).label != null) {
                                requestPickMappingValue(pickableMarker, finalI);
                            } else {
                                commitPick(pickableMarker, finalI, null);
                            }
                        }
                    });
                    ll.addView(b);
                }
            }
            ll.setVisibility(VISIBLE);
            popupWindowInfo.setTouchable(true);
        } else {
            ll.setVisibility(GONE);
            popupWindowInfo.setTouchable(false);
        }
    }

    private void setPopupInfo(int x, int y, String text, Marker pickableMarker) {

        if (popupWindowInfo == null) {
            View pointInfoView = inflate(getContext(), R.layout.point_info, null);
            popupWindowText = pointInfoView.findViewById(R.id.pointInfoText);
            popupWindowInfo = new PopupWindow(
                    pointInfoView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);

            if(Helper.isDarkTheme(getResources())){
                pointInfoView.setBackgroundColor(getResources().getColor(R.color.phyphox_white_100));
                popupWindowText.setTextColor(getResources().getColor(R.color.phyphox_black_100));
            } else{
                pointInfoView.setBackgroundColor(getResources().getColor(R.color.phyphox_black_100));
                popupWindowText.setTextColor(getResources().getColor(R.color.phyphox_white_100));
            }

            popupWindowInfo.setFocusable(false);
            popupWindowInfo.setOutsideTouchable(false);

            updatePopupInfoButtons(pickableMarker);

            popupWindowInfo.showAtLocation(graphFrame, Gravity.BOTTOM | Gravity.CENTER, x, y);
        } else {
            updatePopupInfoButtons(pickableMarker);
            popupWindowInfo.update(x, y, -1, -1);
        }
        popupWindowText.setText(text);
    }

    private void removePopupInfo() {
        if (popupWindowInfo != null) {
            popupWindowInfo.dismiss();
            popupWindowInfo = null;
        }
    }

    private void updateInfo() {
        if (linearRegression) {

            CurveData cd = graphView.graphSetup.dataSets.get(0);
            if (cd == null || cd.fbX == null || cd.fbX.data == null || cd.fbY == null || cd.fbY.data == null)
                return;

            int n;
            int skipped = 0;
            double sumX = 0.;
            double sumX2 = 0.;
            double sumY = 0.;
            double sumY2 = 0.;
            double sumXY = 0.;
            synchronized (cd.fbX) {
                synchronized (cd.fbY) {
                    cd.fbX.data.position(cd.fbX.offset);
                    cd.fbY.data.position(cd.fbY.offset);

                    n = Math.min(cd.fbX.size, cd.fbY.size);
                    for (int i = 0; i < n; i++) {
                        float x = cd.fbX.data.get();
                        float y = cd.fbY.data.get();
                        if (Float.isNaN(x) || Float.isNaN(y)) {
                            skipped++;
                            continue;
                        }
                        sumX += x;
                        sumX2 += x*x;
                        sumY += y;
                        sumY2 += y*y;
                        sumXY += x*y;
                    }
                }
            }

            n -= skipped;

            double norm = n * sumX2 - sumX*sumX;
            if (norm == 0)
                return;

            double a = (n * sumXY  -  sumX * sumY) / norm;
            double b = (sumY * sumX2  -  sumX * sumXY) / norm;

            int pos[] = new int[2];
            graphView.getLocationInWindow(pos);

            Point[] points = new Point[2];
            int viewX1 =(int) Math.round(graphView.dataXToViewX(graphView.minX));
            int viewX2 =(int) Math.round(graphView.dataXToViewX(graphView.maxX));
            int viewY1 =(int) Math.round(graphView.dataYToViewY(graphView.minX * a + b));
            int viewY2 =(int) Math.round(graphView.dataYToViewY(graphView.maxX * a + b));
            points[0] = new Point(viewX1, viewY1);
            points[1] = new Point(viewX2, viewY2);
            markerOverlayView.update(points, null);

            //The fit is done on the data; the read-out shows it in the display units (slope by the scale ratio, b as a position)
            StringBuilder sb = new StringBuilder();
            sb.append(getResources().getString(R.string.graph_fit_label));
            sb.append("\na = ");
            sb.append(String.format("%g", a * graphView.displayScale(GraphView.AXIS_Y) / graphView.displayScale(GraphView.AXIS_X)));
            sb.append(slopeUnit());
            sb.append("\nb = ");
            sb.append(String.format("%g", graphView.toDisplay(GraphView.AXIS_Y, b)));
            sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");

            int infoX = Math.round((viewX1 + viewX2)/2.f + pos[0] - getRootView().getWidth()/2.f);
            int infoY = getRootView().getHeight() - pos[1] - Math.round(Math.min(viewY1, viewY2) - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));

            setPopupInfo(infoX, infoY, sb.toString(), null);


        } else if (marker[0].active && marker[1].active) {

            Point[] points = new Point[2];
            points[0] = new Point(Math.round(marker[0].viewX), Math.round(marker[0].viewY));
            points[1] = new Point(Math.round(marker[1].viewX), Math.round(marker[1].viewY));
            markerOverlayView.update(points, points);

            int pos[] = new int[2];
            graphView.getLocationInWindow(pos);

            int infoX = Math.round((marker[0].viewX + marker[1].viewX)/2.f + pos[0] - getRootView().getWidth()/2.f);
            int infoY = getRootView().getHeight() - pos[1] - Math.round(Math.min(marker[0].viewY, marker[1].viewY) - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));

            //Differences use the scale alone, the slope the ratio of the two scales (units.md, "Temperature", "Slopes")
            double scaleX = graphView.displayScale(GraphView.AXIS_X), scaleY = graphView.displayScale(GraphView.AXIS_Y), scaleZ = graphView.displayScale(GraphView.AXIS_Z);
            StringBuilder sb = new StringBuilder();
            sb.append(getResources().getString(R.string.graph_difference_label));
            sb.append("\n    ");
            sb.append(String.format("%g", Math.abs(marker[0].dataX - marker[1].dataX) * scaleX));
            sb.append(graphView.getUnitX() != null && !graphView.getUnitX().isEmpty() ? " " + graphView.getUnitX() : "");
            sb.append("\n    ");
            sb.append(String.format("%g", Math.abs(marker[0].dataY - marker[1].dataY) * scaleY));
            sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");
            if (!Double.isNaN(marker[0].dataZ) && !Double.isNaN(marker[0].dataZ)) {
                sb.append("\n    ");
                sb.append(String.format("%g", Math.abs(marker[0].dataZ - marker[1].dataZ) * scaleZ));
                sb.append(graphView.getUnitZ() != null && !graphView.getUnitZ().isEmpty() ? " " + graphView.getUnitZ() : "");
            }
            sb.append("\n");
            sb.append(getResources().getString(R.string.graph_slope_label));
            sb.append("\n    ");
            float dx = marker[0].dataX - marker[1].dataX;
            if (dx != 0) {
                sb.append(String.format("%g", (marker[0].dataY - marker[1].dataY) / (marker[0].dataX - marker[1].dataX) * scaleY / scaleX));
                sb.append(slopeUnit());
            } else {
                sb.append("-");
            }

            setPopupInfo(infoX, infoY, sb.toString(), null);

        } else if (marker[0].active || marker[1].active) {

            int pos[] = new int[2];
            graphView.getLocationInWindow(pos);

            Marker activeMarker = marker[0].active ? marker[0] : marker[1];

            Point[] points = new Point[1];
            points[0] = new Point(Math.round(activeMarker.viewX), Math.round(activeMarker.viewY));
            markerOverlayView.update(null, points);

            int infoX = Math.round(activeMarker.viewX - getRootView().getWidth()/2.f + pos[0]);
            int infoY = getRootView().getHeight() - pos[1] - Math.round(activeMarker.viewY - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));

            StringBuilder sb = new StringBuilder();
            sb.append(getResources().getString(R.string.graph_point_label));
            sb.append("\n    ");
            sb.append(String.format("%g", graphView.toDisplay(GraphView.AXIS_X, activeMarker.dataX)));
            sb.append(graphView.getUnitX() != null && !graphView.getUnitX().isEmpty() ? " " + graphView.getUnitX() : "");
            sb.append("\n    ");
            sb.append(String.format("%g", graphView.toDisplay(GraphView.AXIS_Y, activeMarker.dataY)));
            sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");
            if (!Double.isNaN(activeMarker.dataZ)) {
                sb.append("\n    ");
                sb.append(String.format("%g", graphView.toDisplay(GraphView.AXIS_Z, activeMarker.dataZ)));
                sb.append(graphView.getUnitZ() != null && !graphView.getUnitZ().isEmpty() ? " " + graphView.getUnitZ() : "");
            }


            setPopupInfo(infoX, infoY, sb.toString(), activeMarker);

        } else {
            removePopUpAndMarkerOverlayView();
        }
        updatePickMarker();
    }

    public void hidePointInfo(int index) {
        marker[index].remove();
    }

    public void showPointInfo(float viewX, float viewY, float pointX, float pointY, float pointZ, int index) {
        if (index >= markerMax)
            return;
        if (Float.isInfinite(viewX) || Float.isNaN(viewX) || Float.isInfinite(viewY) || Float.isNaN(viewY)) {
            marker[index].remove();
            return;
        }

        marker[index].set(viewX, viewY, pointX, pointY, pointZ);
    }

    public void stop() {
        linearRegression = false;
        graphView.resetPicks();
        plotRenderer.halt();
        try {
            plotRenderer.join();
        } catch (InterruptedException e) {
            Log.e("cleanView", "Renderer: Interrupted execution.");
        }
        plotRenderer = null;
    }

    public void setShowColorScale(boolean showColorScale){
        if(this.plotRenderer != null)
            this.plotRenderer.setShowColorScaleForColorMapChart(showColorScale);

        if(this.graphView != null)
            this.graphView.setShowColorScaleForColorMapChart(showColorScale);
    }

    public void setPickConfig(String pickLabel, Vector<DataOutput> outputs, PickerObserver observer) {
        this.pickLabel = pickLabel;
        this.outputs = outputs;
        this.observer = observer;

        if (pickLabel != null) {
            ((TextView)(toolbar.findViewById(R.id.graph_tools_pick).findViewById(R.id.item_title))).setText(pickLabel);
        }

    }

    public void updatePickData(Double[] newData) {
        pickData = newData;
        updatePickMarker();
    }

    public void updatePickMarker() {
        Vector<MarkerOverlayView.LineAnnotation> lineAnnotations = new Vector<>();
        for (int i = 0; i < outputs.size(); i+=2) {
            if ((i / 2) % 3 == 2)
                continue; // z axis annotations are not shown
            if (outputs.get(i) != null && pickData[i] != null && !pickData[i].isNaN()) {
                String label = outputs.get(i).label;
                if (outputs.size() > i+1 && outputs.get(i+1) != null && pickData[i+1] != null && !pickData[i+1].isNaN())
                    label += " → " + String.valueOf(pickData[i+1]);
                boolean vertical = (i / 2) % 3 == 0;
                float xy = (float)(vertical ? graphView.dataXToViewX(pickData[i].floatValue()) : graphView.dataYToViewY(pickData[i].floatValue()));
                lineAnnotations.add((markerOverlayView.new LineAnnotation(label, xy, (i / 2) % 3 == 0, getResources().getColor(R.color.phyphox_green))));
            }
        }

        markerOverlayView.setLineAnnotations(lineAnnotations.toArray(new MarkerOverlayView.LineAnnotation[0]));
    }
}
