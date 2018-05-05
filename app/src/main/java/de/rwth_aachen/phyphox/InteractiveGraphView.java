package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.internal.BottomNavigationMenu;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toolbar;

import org.w3c.dom.Text;

import java.util.Vector;

public class InteractiveGraphView extends RelativeLayout implements GraphView.PointInfo {

    private boolean interactive = false;
    public GraphView graphView;
    private TextView graphLabel;
    private ImageView expandImage, collapseImage;
    private BottomNavigationView toolbar;

    private PlotRenderer plotRenderer = null;

    View rootView;
    FrameLayout graphFrame;

    private class Marker {
        boolean active = false;
        PopupWindow popupWindowMarker = null;
        ImageView markerView = null;
        float viewX, viewY;
        float dataX, dataY;

        Marker() {

        }

        public void remove() {
            active = false;
            if (popupWindowMarker != null) {
                popupWindowMarker.dismiss();
                popupWindowMarker = null;
            }
            updateInfo();
        }

        public void set(float viewX, float viewY, float dataX, float dataY) {
            active = true;
            this.viewX = viewX;
            this.viewY = viewY;
            this.dataX = dataX;
            this.dataY = dataY;

            int markerX = Math.round(viewX - getRootView().getWidth()/2.f);
            int markerY = Math.round(viewY - getRootView().getHeight()/2.f);

            if (popupWindowMarker == null) {
                markerView = new ImageView(getContext());
                markerView.setImageResource(R.drawable.point_marker);
                popupWindowMarker = new PopupWindow(markerView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (Build.VERSION.SDK_INT >= 21){
                    popupWindowMarker.setElevation(4.0f);
                }
                popupWindowMarker.setOutsideTouchable(false);
                popupWindowMarker.setTouchable(false);
                popupWindowMarker.setFocusable(false);
                popupWindowMarker.showAtLocation(graphFrame,  Gravity.CENTER, markerX, markerY);
            } else {
                popupWindowMarker.update(markerX, markerY, -1, -1);
            }
            updateInfo();
        }
    }

    final int markerMax = 2;
    Marker marker[] = new Marker[markerMax];
    PopupWindow popupWindowInfo = null;
    TextView popupWindowText = null;

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
                        for (int i = 0; i < markerMax; i++)
                            marker[i].remove();
                        graphView.setTouchMode(GraphView.TouchMode.zoom);
                        return true;
                    case R.id.graph_tools_pick:
                        graphView.setTouchMode(GraphView.TouchMode.pick);
                        return true;
                    case R.id.graph_tools_more:
                        PopupMenu popup = new PopupMenu(getContext(), findViewById(R.id.graph_tools_more));
                        popup.getMenuInflater().inflate(R.menu.graph_tools_menu, popup.getMenu());
                        popup.getMenu().findItem(R.id.graph_tools_follow).setChecked(graphView.zoomFollows);

                        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                switch (menuItem.getItemId()) {
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
                                }
                                return false;
                            }
                        });

                        popup.show();
                }
                for (int i = 0; i < markerMax; i++)
                    marker[i].remove();
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
            for (int i = 0; i < markerMax; i++)
                marker[i].remove();
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

    private void updateInfo() {
        if (marker[0].active && marker[1].active) {
            int infoX = Math.round((marker[0].viewX + marker[1].viewX)/2.f - getRootView().getWidth()/2.f);
            int infoY = getRootView().getHeight() - Math.round(Math.min(marker[0].viewY, marker[1].viewY) - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));

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
            Marker activeMarker = marker[0].active ? marker[0] : marker[1];

            int infoX = Math.round(activeMarker.viewX - getRootView().getWidth()/2.f);
            int infoY = getRootView().getHeight() - Math.round(activeMarker.viewY - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));

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
            if (popupWindowInfo != null) {
                popupWindowInfo.dismiss();
                popupWindowInfo = null;
            }
        }
    }

    public void showPointInfo(float viewX, float viewY, float pointX, float pointY, int index) {
        if (index >= markerMax)
            return;
        if (Float.isInfinite(viewX) || Float.isNaN(viewX) || Float.isInfinite(viewY) || Float.isNaN(viewY)) {
            marker[index].remove();
            return;
        }

        if (viewX < graphView.graphSetup.plotBoundL|| viewX > graphView.graphSetup.plotBoundL + graphView.graphSetup.plotBoundW
                || viewY < graphView.graphSetup.plotBoundT || viewY > graphView.graphSetup.plotBoundT + graphView.graphSetup.plotBoundH) {
            marker[index].remove();
            return;
        }

        int pos[] = new int[2];
        graphView.getLocationInWindow(pos);

        float x = viewX + pos[0];
        float y = viewY + pos[1];

        marker[index].set(x, y, pointX, pointY);
    }

    public void stop() {
        for (int i = 0; i < markerMax; i++)
            marker[i].remove();
        plotRenderer.halt();
        try {
            plotRenderer.join();
        } catch (InterruptedException e) {
            Log.e("cleanView", "Renderer: Interrupted execution.");
        }
        plotRenderer = null;
    }

}
