package de.rwth_aachen.phyphox;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.internal.BottomNavigationMenu;
import android.support.design.widget.BottomNavigationView;
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
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toolbar;

import org.w3c.dom.Text;

public class InteractiveGraphView extends RelativeLayout implements GraphView.PointInfo {

    private boolean interactive = false;
    public GraphView graphView;
    private TextView graphLabel;
    private ImageView expandImage, collapseImage;
    private BottomNavigationView toolbar;

    private PlotRenderer plotRenderer = null;

    View rootView;
    FrameLayout graphFrame;

    PopupWindow popupWindowInfo = null;
    PopupWindow popupWindowMarker = null;
    TextView popupWindowText = null;
    ImageView markerView = null;

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
                        removeMarker();
                        graphView.setTouchMode(GraphView.TouchMode.zoom);
                        return true;
                    case R.id.graph_tools_pick:
                        graphView.setTouchMode(GraphView.TouchMode.pick);
                        return true;
                }
                removeMarker();
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
        graphView.setPointInfoListener(this);

        graphFrame.addView(plotAreaView);
        graphFrame.addView(graphView);
    }

    public void setInteractive(boolean interactive) {
        if (!interactive) {
            graphView.setTouchMode(GraphView.TouchMode.off);
            removeMarker();
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

    private void removeMarker() {
        if (popupWindowMarker != null) {
            popupWindowMarker.dismiss();
            popupWindowMarker = null;
        }
        if (popupWindowInfo != null) {
            popupWindowInfo.dismiss();
            popupWindowInfo = null;
        }
    }

    public void showPointInfo(float viewX, float viewY, float pointX, float pointY) {
        if (Float.isInfinite(viewX) || Float.isNaN(viewX) || Float.isInfinite(viewY) || Float.isNaN(viewY)) {
            removeMarker();
            return;
        }
        int pos[] = new int[2];
        graphView.getLocationInWindow(pos);

        float x = viewX + pos[0];
        float y = viewY + pos[1];

        int markerX = Math.round(x - getRootView().getWidth()/2.f);
        int markerY = Math.round(y - getRootView().getHeight()/2.f);

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

        int infoX = markerX;
        int infoY = getRootView().getHeight() - Math.round(y - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));

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
            popupWindowInfo.showAtLocation(graphFrame, Gravity.BOTTOM | Gravity.CENTER, infoX, infoY);
        } else {
            popupWindowInfo.update(infoX, infoY, -1, -1);
        }
        popupWindowText.setText("(" + pointX + ", " + pointY + ")");

    }

    public void stop() {
        plotRenderer.halt();
        try {
            plotRenderer.join();
        } catch (InterruptedException e) {
            Log.e("cleanView", "Renderer: Interrupted execution.");
        }
        plotRenderer = null;
    }

}
