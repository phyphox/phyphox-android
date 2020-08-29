package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class InteractiveGraphView extends RelativeLayout implements GraphView.PointInfo {

    private boolean interactive = false;
    private boolean linearRegression = false;
    public GraphView graphView;
    private TextView graphLabel;
    private ImageView expandImage, collapseImage;
    private BottomNavigationView toolbar;
    public boolean allowLogX = false;
    public boolean allowLogY = false;

    private PlotRenderer plotRenderer = null;

    private DataExport dataExport = null;

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
    TextView popupWindowText = null;
    MarkerOverlayView markerOverlayView;

    public InteractiveGraphView(Context context) {
        super(context);
        init(context);
    }

    public InteractiveGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        rootView = inflate(context, R.layout.interactive_graph_layout, this);

        for (int i = 0; i < markerMax; i++)
            marker[i] = new Marker();

        graphFrame = (FrameLayout)this.findViewById(R.id.graph_frame);
        graphLabel = (TextView)this.findViewById(R.id.graph_label);
        expandImage = (ImageView)this.findViewById(R.id.graph_expand_image);
        collapseImage = (ImageView)this.findViewById(R.id.graph_collapse_image);
        toolbar = (BottomNavigationView) this.findViewById(R.id.graph_toolbar);

        toolbar.inflateMenu(R.menu.graph_menu);
        toolbar.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.graph_tools_pan:
                        graphView.setTouchMode(GraphView.TouchMode.zoom);
                        return true;
                    case R.id.graph_tools_pick:
                        graphView.setTouchMode(GraphView.TouchMode.pick);
                        return true;
                    case R.id.graph_tools_more:
                        PopupMenu popup = new PopupMenu(getContext(), findViewById(R.id.graph_tools_more));
                        popup.getMenuInflater().inflate(R.menu.graph_tools_menu, popup.getMenu());
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

                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                switch (menuItem.getItemId()) {
                                    case R.id.graph_tools_linear_fit:
                                        linearRegression = !linearRegression;
                                        graphView.resetPicks();
                                        updateInfo();
                                        break;
                                    case R.id.graph_tools_reset:
                                        graphView.zoomState.follows = false;
                                        graphView.zoomState.minX = Double.NaN;
                                        graphView.zoomState.maxX = Double.NaN;
                                        graphView.zoomState.minY = Double.NaN;
                                        graphView.zoomState.maxY = Double.NaN;
                                        graphView.zoomState.minZ = Double.NaN;
                                        graphView.zoomState.maxZ = Double.NaN;
                                        graphView.invalidate();
                                        break;
                                    case R.id.graph_tools_follow:
                                        if (Double.isNaN(graphView.zoomState.minX) || Double.isNaN(graphView.zoomState.maxX)) {
                                            graphView.zoomState.minX = graphView.minX;
                                            graphView.zoomState.maxX = graphView.maxX;
                                        }
                                        graphView.zoomState.follows = !graphView.zoomState.follows;
                                        graphView.invalidate();
                                        break;
                                    case R.id.graph_tools_export:
                                        Context ctx = getContext();
                                        Activity act = null;
                                        while (ctx instanceof ContextWrapper) {
                                            if (ctx instanceof Activity) {
                                                act = (Activity) ctx;
                                            }
                                            ctx = ((ContextWrapper)ctx).getBaseContext();
                                        }
                                        if (act == null)
                                            break;
                                        dataExport.export(act, true);
                                        break;
                                    case R.id.graph_tools_log_x:
                                        graphView.setLogScale(!graphView.logX, graphView.logY, graphView.logZ);
                                        graphView.invalidate();
                                        break;
                                    case R.id.graph_tools_log_y:
                                        graphView.setLogScale(graphView.logX, !graphView.logY, graphView.logZ);
                                        graphView.invalidate();
                                        break;
                                }
                                return false;
                            }
                        });

                        popup.show();
                }
                return false;
            }
        });

        PlotAreaView plotAreaView = new PlotAreaView(context);
        plotAreaView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        plotRenderer = new PlotRenderer(context.getResources());
        plotRenderer.start();
        plotAreaView.setSurfaceTextureListener(plotRenderer);

        this.graphView = new GraphView(context, plotAreaView, plotRenderer);
        graphView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        graphView.setPointInfoListener(this);

        graphFrame.addView(plotAreaView);
        graphFrame.addView(graphView);

        markerOverlayView = new MarkerOverlayView(getContext());
        markerOverlayView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        markerOverlayView.setGraphSetup(graphView.graphSetup);
        graphFrame.addView(markerOverlayView);
    }

    public void assignDataExporter(DataExport dataExport) {
        this.dataExport = dataExport;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    public void leaveDialog(final ExpViewFragment parent, final String bufferX, final String bufferY, final String unitX, final String unitY) {
        if (Double.isNaN(graphView.zoomState.minX) && Double.isNaN(graphView.zoomState.minY) && Double.isNaN(graphView.zoomState.maxX) && Double.isNaN(graphView.zoomState.maxY) && Double.isNaN(graphView.zoomState.maxZ) && Double.isNaN(graphView.zoomState.maxZ)) {
            parent.leaveExclusive();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        final View dialogView = inflate(getContext(), R.layout.apply_zoom_dialog, null);
        builder.setView(dialogView);
        final TextView tvLabelX = (TextView) dialogView.findViewById(R.id.applyZoomXLabel);
        final TextView tvLabelY = (TextView) dialogView.findViewById(R.id.applyZoomYLabel);
        final TextView tvLabelZ = (TextView) dialogView.findViewById(R.id.applyZoomZLabel);
        final RadioButton rbReset = (RadioButton) dialogView.findViewById(R.id.applyZoomReset);
        final RadioButton rbKeep = (RadioButton) dialogView.findViewById(R.id.applyZoomKeep);
        final RadioButton rbResetX = (RadioButton) dialogView.findViewById(R.id.applyZoomXReset);
        final RadioButton rbKeepX = (RadioButton) dialogView.findViewById(R.id.applyZoomXKeep);
        final RadioButton rbFollowX = (RadioButton) dialogView.findViewById(R.id.applyZoomXFollow);
        final RadioButton rbResetY = (RadioButton) dialogView.findViewById(R.id.applyZoomYReset);
        final RadioButton rbKeepY = (RadioButton) dialogView.findViewById(R.id.applyZoomYKeep);
        final RadioButton rbResetZ = (RadioButton) dialogView.findViewById(R.id.applyZoomZReset);
        final RadioButton rbKeepZ = (RadioButton) dialogView.findViewById(R.id.applyZoomZKeep);
        final Spinner sApplyX = (Spinner) dialogView.findViewById(R.id.applyZoomXApplyTo);
        final Spinner sApplyY = (Spinner) dialogView.findViewById(R.id.applyZoomYApplyTo);
        final Switch swAdvanced = (Switch) dialogView.findViewById(R.id.applyZoomAdvanced);

        final RadioGroup rgGenericOptions = (RadioGroup) dialogView.findViewById(R.id.applyZoomMode);
        final GridLayout glXOptions = (GridLayout)dialogView.findViewById(R.id.applyZoomX);
        final GridLayout glYOptions = (GridLayout)dialogView.findViewById(R.id.applyZoomY);
        final GridLayout glZOptions = (GridLayout)dialogView.findViewById(R.id.applyZoomZ);

        boolean hasZAxis = false;
        for (int i = 0; i < graphView.style.length; i++) {
            if (graphView.style[i] == GraphView.Style.mapZ)
                hasZAxis = true;
        }
        final boolean zShown = hasZAxis;

        swAdvanced.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    glXOptions.setVisibility(VISIBLE);
                    glYOptions.setVisibility(VISIBLE);
                    glZOptions.setVisibility(zShown ? VISIBLE : GONE);
                    rgGenericOptions.setVisibility(GONE);
                } else {
                    glXOptions.setVisibility(GONE);
                    glYOptions.setVisibility(GONE);
                    glZOptions.setVisibility(GONE);
                    rgGenericOptions.setVisibility(VISIBLE);
                }
            }
        });

        tvLabelX.setText(graphView.getLabelAndUnitX());
        tvLabelY.setText(graphView.getLabelAndUnitY());
        tvLabelZ.setText(graphView.getLabelAndUnitZ());
        rbFollowX.setVisibility(graphView.graphSetup.incrementalX ? VISIBLE : GONE);

        if (graphView.previouslyKept) {
            rbKeep.setChecked(true);
        } else {
            rbReset.setChecked(true);
        }

        if (graphView.zoomState.follows && graphView.graphSetup.incrementalX && !Double.isNaN(graphView.zoomState.minX) && !Double.isNaN(graphView.zoomState.maxX)) {
            rbFollowX.setChecked(true);
        } else if (!Double.isNaN(graphView.zoomState.minX) && !Double.isNaN(graphView.zoomState.maxX)) {
            rbKeepX.setChecked(true);
        } else {
            rbResetX.setChecked(true);
        }

        if (!Double.isNaN(graphView.zoomState.minY) && !Double.isNaN(graphView.zoomState.maxY)) {
            rbKeepY.setChecked(true);
        } else {
            rbResetY.setChecked(true);
        }

        if (zShown) {
            if (!Double.isNaN(graphView.zoomState.minZ) && !Double.isNaN(graphView.zoomState.maxZ)) {
                rbKeepZ.setChecked(true);
            } else {
                rbResetZ.setChecked(true);
            }
        }

        builder.setTitle(R.string.applyZoomTitle)
                .setPositiveButton(R.string.ok, (dialog, id) -> {
                    double minX, maxX, minY, maxY, minZ, maxZ;
                    boolean simple = !swAdvanced.isChecked();

                    graphView.previouslyKept = (simple && rbKeep.isChecked()) || (!simple && (rbKeepX.isChecked() || rbKeepY.isChecked() || rbKeepZ.isChecked()));

                    if ((simple && rbReset.isChecked()) || (!simple && rbResetX.isChecked())) {
                        minX = Double.NaN;
                        maxX = Double.NaN;
                    } else {
                        minX = graphView.zoomState.minX;
                        maxX = graphView.zoomState.maxX;
                    }
                    if ((simple && rbReset.isChecked()) || (!simple && rbResetY.isChecked())) {
                        minY = Double.NaN;
                        maxY = Double.NaN;
                    } else {
                        minY = graphView.zoomState.minY;
                        maxY = graphView.zoomState.maxY;
                    }
                    graphView.zoomState.minX = minX;
                    graphView.zoomState.maxX = maxX;
                    graphView.zoomState.minY = minY;
                    graphView.zoomState.maxY = maxY;
                    graphView.zoomState.follows = (simple && graphView.zoomState.follows) || (!simple && rbFollowX.isChecked());

                    if (!simple) {

                        switch (sApplyX.getSelectedItemPosition()) {
                            case 1:
                                parent.applyZoom(minX, maxX, rbFollowX.isChecked(), null, bufferX, false);
                                break;
                            case 2:
                                parent.applyZoom(minX, maxX, rbFollowX.isChecked(), unitX, null, false);
                                break;
                            case 3:
                                parent.applyZoom(minX, maxX, rbFollowX.isChecked(), null, null, false);
                                break;
                        }

                        switch (sApplyY.getSelectedItemPosition()) {
                            case 1:
                                parent.applyZoom(minY, maxY, false, null, bufferY, true);
                                break;
                            case 2:
                                parent.applyZoom(minY, maxY, false, unitY, null, true);
                                break;
                            case 3:
                                parent.applyZoom(minY, maxY, false, null, null, true);
                                break;
                        }
                    }

                    if (zShown) {
                        if ((simple && rbReset.isChecked()) || (!simple && rbResetZ.isChecked())) {
                            minZ = Double.NaN;
                            maxZ = Double.NaN;
                        } else {
                            minZ = graphView.zoomState.minZ;
                            maxZ = graphView.zoomState.maxZ;
                        }
                        graphView.zoomState.minZ = minZ;
                        graphView.zoomState.maxZ = maxZ;
                    }

                    parent.leaveExclusive();
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void setInteractive(boolean interactive) {

        if (!interactive) {
            graphView.setTouchMode(GraphView.TouchMode.off);
            linearRegression = false;
            graphView.resetPicks();
        }
        else if (toolbar.getSelectedItemId() == R.id.graph_tools_pan)
            graphView.setTouchMode(GraphView.TouchMode.zoom);
        else if (toolbar.getSelectedItemId() == R.id.graph_tools_pick)
            graphView.setTouchMode(GraphView.TouchMode.pick);

        toolbar.setVisibility(interactive ? VISIBLE : GONE);

        expandImage.setVisibility(interactive ? INVISIBLE : VISIBLE);
        collapseImage.setVisibility(interactive ? VISIBLE : INVISIBLE);

        this.interactive = interactive;
    }

    public void setLabel(String label) {
        graphLabel.setText(label);
    }

    private void setPopupInfo(int x, int y, String text) {
        if (popupWindowInfo == null) {
            View pointInfoView = inflate(getContext(), R.layout.point_info, null);
            popupWindowText = (TextView)pointInfoView.findViewById(R.id.point_info_text);
            popupWindowInfo = new PopupWindow(pointInfoView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (Build.VERSION.SDK_INT >= 21){
                popupWindowInfo.setElevation(4.0f);
            }
            popupWindowInfo.setOutsideTouchable(false);
            popupWindowInfo.setTouchable(false);
            popupWindowInfo.setFocusable(false);
            popupWindowInfo.showAtLocation(graphFrame, Gravity.BOTTOM | Gravity.CENTER, x, y);
        } else {
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

            StringBuilder sb = new StringBuilder();
            sb.append(getResources().getString(R.string.graph_fit_label));
            sb.append("\na = ");
            sb.append((float)a);
            if (graphView.getUnitYX() != null)
                sb.append(graphView.getUnitYX());
            else {
                sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");
                sb.append(" / ");
                sb.append(graphView.getUnitX() != null && !graphView.getUnitX().isEmpty() ? " " + graphView.getUnitX() : "");
            }
            sb.append("\nb = ");
            sb.append((float)b);
            sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");

            int infoX = Math.round((viewX1 + viewX2)/2.f + pos[0] - getRootView().getWidth()/2.f);
            int infoY = getRootView().getHeight() - pos[1] - Math.round(Math.min(viewY1, viewY2) - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));

            setPopupInfo(infoX, infoY, sb.toString());


        } else if (marker[0].active && marker[1].active) {

            Point[] points = new Point[2];
            points[0] = new Point(Math.round(marker[0].viewX), Math.round(marker[0].viewY));
            points[1] = new Point(Math.round(marker[1].viewX), Math.round(marker[1].viewY));
            markerOverlayView.update(points, points);

            int pos[] = new int[2];
            graphView.getLocationInWindow(pos);

            int infoX = Math.round((marker[0].viewX + marker[1].viewX)/2.f + pos[0] - getRootView().getWidth()/2.f);
            int infoY = getRootView().getHeight() - pos[1] - Math.round(Math.min(marker[0].viewY, marker[1].viewY) - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));

            StringBuilder sb = new StringBuilder();
            sb.append(getResources().getString(R.string.graph_difference_label));
            sb.append("\n    ");
            sb.append(Math.abs(marker[0].dataX - marker[1].dataX));
            sb.append(graphView.getUnitX() != null && !graphView.getUnitX().isEmpty() ? " " + graphView.getUnitX() : "");
            sb.append("\n    ");
            sb.append(Math.abs(marker[0].dataY - marker[1].dataY));
            sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");
            if (!Double.isNaN(marker[0].dataZ) && !Double.isNaN(marker[0].dataZ)) {
                sb.append("\n    ");
                sb.append(Math.abs(marker[0].dataZ - marker[1].dataZ));
                sb.append(graphView.getUnitZ() != null && !graphView.getUnitZ().isEmpty() ? " " + graphView.getUnitZ() : "");
            }
            sb.append("\n");
            sb.append(getResources().getString(R.string.graph_slope_label));
            sb.append("\n    ");
            float dx = marker[0].dataX - marker[1].dataX;
            if (dx != 0) {
                sb.append((marker[0].dataY - marker[1].dataY) / (marker[0].dataX - marker[1].dataX));
                if (graphView.getUnitYX() != null)
                    sb.append(graphView.getUnitYX());
                else {
                    sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");
                    sb.append(" / ");
                    sb.append(graphView.getUnitX() != null && !graphView.getUnitX().isEmpty() ? " " + graphView.getUnitX() : "");
                }
            } else {
                sb.append("-");
            }

            setPopupInfo(infoX, infoY, sb.toString());

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
            sb.append(activeMarker.dataX);
            sb.append(graphView.getUnitX() != null && !graphView.getUnitX().isEmpty() ? " " + graphView.getUnitX() : "");
            sb.append("\n    ");
            sb.append(activeMarker.dataY);
            sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");
            if (!Double.isNaN(activeMarker.dataZ)) {
                sb.append("\n    ");
                sb.append(activeMarker.dataZ);
                sb.append(graphView.getUnitZ() != null && !graphView.getUnitZ().isEmpty() ? " " + graphView.getUnitZ() : "");
            }


            setPopupInfo(infoX, infoY, sb.toString());

        } else {
            removePopupInfo();
            markerOverlayView.update(null, null);
        }
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

}
