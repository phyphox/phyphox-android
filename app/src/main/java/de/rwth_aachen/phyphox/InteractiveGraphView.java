package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class InteractiveGraphView extends RelativeLayout implements GraphView.PointInfo {

    private boolean interactive = false;
    private boolean linearRegression = false;
    public GraphView graphView;
    private TextView graphLabel;
    private ImageView expandImage, collapseImage;
    private BottomNavigationView toolbar;

    private PlotRenderer plotRenderer = null;

    private DataExport dataExport = null;

    View rootView;
    FrameLayout graphFrame;

    private class Marker {
        boolean active = false;
        float viewX, viewY;
        float dataX, dataY;

        Marker() {
        }

        public void remove() {
            active = false;
            updateInfo();
        }

        public void set(float viewX, float viewY, float dataX, float dataY) {
            linearRegression = false;

            active = true;
            this.viewX = viewX;
            this.viewY = viewY;
            this.dataX = dataX;
            this.dataY = dataY;

            updateInfo();
        }
    }

    final int markerMax = 2;
    Marker marker[] = new Marker[markerMax];
    PopupWindow popupWindowInfo = null;
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
                        popup.getMenu().findItem(R.id.graph_tools_follow).setChecked(graphView.zoomFollows);
                        popup.getMenu().findItem(R.id.graph_tools_export).setVisible(dataExport != null);

                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                switch (menuItem.getItemId()) {
                                    case R.id.graph_tools_linear_fit:
                                        linearRegression = true;
                                        graphView.resetPicks();
                                        updateInfo();
                                        break;
                                    case R.id.graph_tools_reset:
                                        graphView.zoomFollows = false;
                                        graphView.zoomMinX = Double.NaN;
                                        graphView.zoomMaxX = Double.NaN;
                                        graphView.zoomMinY = Double.NaN;
                                        graphView.zoomMaxY = Double.NaN;
                                        graphView.invalidate();
                                        break;
                                    case R.id.graph_tools_follow:
                                        if (Double.isNaN(graphView.zoomMinX) || Double.isNaN(graphView.zoomMaxX)) {
                                            graphView.zoomMinX = graphView.minX;
                                            graphView.zoomMaxX = graphView.maxX;
                                        }
                                        graphView.zoomFollows = !graphView.zoomFollows;
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

    public void leaveDialog(final expViewFragment parent, boolean canFollow, final String bufferX, final String bufferY, final String unitX, final String unitY) {
        if (Double.isNaN(graphView.zoomMinX) && Double.isNaN(graphView.zoomMinY) && Double.isNaN(graphView.zoomMaxX) && Double.isNaN(graphView.zoomMaxY)) {
            parent.leaveExclusive();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        final View dialogView = inflate(getContext(), R.layout.apply_zoom_dialog, null);
        builder.setView(dialogView);
        final TextView tvLabelX = (TextView) dialogView.findViewById(R.id.applyZoomXLabel);
        final TextView tvLabelY = (TextView) dialogView.findViewById(R.id.applyZoomYLabel);
        final RadioButton rbResetX = (RadioButton) dialogView.findViewById(R.id.applyZoomXReset);
        final RadioButton rbKeepX = (RadioButton) dialogView.findViewById(R.id.applyZoomXKeep);
        final RadioButton rbFollowX = (RadioButton) dialogView.findViewById(R.id.applyZoomXFollow);
        final RadioButton rbResetY = (RadioButton) dialogView.findViewById(R.id.applyZoomYReset);
        final RadioButton rbKeepY = (RadioButton) dialogView.findViewById(R.id.applyZoomYKeep);
        final Spinner sApplyX = (Spinner) dialogView.findViewById(R.id.applyZoomXApplyTo);
        final Spinner sApplyY = (Spinner) dialogView.findViewById(R.id.applyZoomYApplyTo);
        tvLabelX.setText(graphView.getLabelAndUnitX());
        tvLabelY.setText(graphView.getLabelAndUnitY());
        rbFollowX.setVisibility(canFollow ? VISIBLE : GONE);
        if (graphView.zoomFollows && canFollow && !Double.isNaN(graphView.zoomMinX) && !Double.isNaN(graphView.zoomMaxX)) {
            rbFollowX.setChecked(true);
        } else if (!Double.isNaN(graphView.zoomMinX) && !Double.isNaN(graphView.zoomMaxX)) {
            rbKeepX.setChecked(true);
        } else {
            rbResetX.setChecked(true);
        }

        if (!Double.isNaN(graphView.zoomMinY) && !Double.isNaN(graphView.zoomMaxY)) {
            rbKeepY.setChecked(true);
        } else {
            rbResetY.setChecked(true);
        }

        builder.setTitle(R.string.applyZoomTitle)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        double minX, maxX, minY, maxY;
                        if (rbResetX.isChecked()) {
                            minX = Double.NaN;
                            maxX = Double.NaN;
                        } else {
                            minX = graphView.zoomMinX;
                            maxX = graphView.zoomMaxX;
                        }
                        if (rbResetY.isChecked()) {
                            minY = Double.NaN;
                            maxY = Double.NaN;
                        } else {
                            minY = graphView.zoomMinY;
                            maxY = graphView.zoomMaxY;
                        }
                        graphView.zoomMinX = minX;
                        graphView.zoomMaxX = maxX;
                        graphView.zoomMinY = minY;
                        graphView.zoomMaxY = maxY;
                        graphView.zoomFollows = rbFollowX.isChecked();

                        switch (sApplyX.getSelectedItemPosition()) {
                            case 1: parent.applyZoom(minX, maxX, rbFollowX.isChecked(), null, bufferX, false);
                                break;
                            case 2: parent.applyZoom(minX, maxX, rbFollowX.isChecked(), unitX, null, false);
                                break;
                            case 3: parent.applyZoom(minX, maxX, rbFollowX.isChecked(), null, null, false);
                                break;
                        }

                        switch (sApplyY.getSelectedItemPosition()) {
                            case 1: parent.applyZoom(minY, maxY, false, null, bufferY, true);
                                break;
                            case 2: parent.applyZoom(minY, maxY, false, unitY, null, true);
                                break;
                            case 3: parent.applyZoom(minY, maxY, false, null, null, true);
                                break;
                        }

                        parent.leaveExclusive();
                    }
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
                        sumX += x;
                        sumX2 += x*x;
                        sumY += y;
                        sumY2 += y*y;
                        sumXY += x*y;
                    }
                }
            }

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
            sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");
            sb.append(" / ");
            sb.append(graphView.getUnitX() != null && !graphView.getUnitX().isEmpty() ? " " + graphView.getUnitX() : "");
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
            sb.append("\n");
            sb.append(getResources().getString(R.string.graph_slope_label));
            sb.append("\n    ");
            float dx = marker[0].dataX - marker[1].dataX;
            if (dx != 0) {
                sb.append((marker[0].dataY - marker[1].dataY) / (marker[0].dataX - marker[1].dataX));
                sb.append(graphView.getUnitY() != null && !graphView.getUnitY().isEmpty() ? " " + graphView.getUnitY() : "");
                sb.append(" / ");
                sb.append(graphView.getUnitX() != null && !graphView.getUnitX().isEmpty() ? " " + graphView.getUnitX() : "");
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


            setPopupInfo(infoX, infoY, sb.toString());

        } else {
            removePopupInfo();
            markerOverlayView.update(null, null);
        }
    }

    public void showPointInfo(float viewX, float viewY, float pointX, float pointY, int index) {
        if (index >= markerMax)
            return;
        if (Float.isInfinite(viewX) || Float.isNaN(viewX) || Float.isInfinite(viewY) || Float.isNaN(viewY)) {
            marker[index].remove();
            return;
        }

        marker[index].set(viewX, viewY, pointX, pointY);
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
